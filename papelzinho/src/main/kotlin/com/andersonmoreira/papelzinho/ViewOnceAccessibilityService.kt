package com.andersonmoreira.papelzinho

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.andersonmoreira.papelzinho.selectors.WhatsAppSelectors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ViewOnceAccessibilityService : AccessibilityService() {
    private val app get() = application as PapelzinhoApplication
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var target: WhatsAppTargetResolver.Target? = null
    private var lastObservation: String? = null
    private val engine by lazy {
        ViewOnceAutomation(app.sessions, SystemClock::elapsedRealtime, ::click) { terminal ->
            Log.i("Papelzinho", "state=$terminal elapsed=${SystemClock.elapsedRealtime()}")
            ResultNotifier(this).notify(terminal)
            if (terminal == SessionState.DONE) {
                target?.let { current ->
                    scope.launch {
                        SettingsStore(
                            this@ViewOnceAccessibilityService,
                        ).recordSuccess(current.packageName, current.version)
                    }
                }
            }
        }
    }
    private val timer =
        object : Runnable {
            override fun run() {
                if (app.sessions.active() != null) engine.tick()
                handler.postDelayed(this, SessionTiming.SERVICE_POLL_MS)
            }
        }

    override fun onServiceConnected() {
        Log.i("Papelzinho", "service=connected")
        handler.post(timer)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val session = app.sessions.active() ?: return
        if (event?.packageName?.toString() != session.targetPackage) return
        val resolver = WhatsAppTargetResolver(this)
        target = resolver.installed().firstOrNull { it.packageName == session.targetPackage }
        if (target?.let(resolver::supported) != true) {
            app.sessions.release(session.token, SessionState.ABORTED)
            ResultNotifier(this).notify(SessionState.ABORTED)
        } else {
            activeRoot(session)?.let { root ->
                val before = engine.state
                val selectors = WhatsAppSelectors.forRoute(session.route)
                val tree = flatten(root)
                val snapshot = selectors.snapshot(tree.map { it.first })
                val previewNode =
                    tree.firstOrNull { it.first.resourceId in selectors.previewScreen.resourceIds }?.second
                val observation =
                    "route=${session.route} targetRead=${session.targetHasRead} " +
                        "keyboardRead=${session.keyboardHasRead} " +
                        "preview=${snapshot.preview} toggle=${snapshot.toggle} " +
                        "active=${snapshot.active} send=${snapshot.send} " +
                        "previewPresent=${previewNode != null} " +
                        "previewEnabled=${previewNode?.isEnabled} previewVisible=${previewNode?.isVisibleToUser}"
                if (observation != lastObservation) {
                    Log.i("Papelzinho", "observe $observation")
                    lastObservation = observation
                }
                engine.onSnapshot(
                    session.targetPackage,
                    snapshot,
                )
                if (before != engine.state) {
                    Log.i("Papelzinho", "state=${engine.state} elapsed=${SystemClock.elapsedRealtime()}")
                }
            }
        }
    }

    private fun activeRoot(session: SendSession): AccessibilityNodeInfo? =
        rootInActiveWindow?.takeIf {
            it.packageName?.toString() == session.targetPackage
        }

    private fun click(action: Action): Boolean {
        val session = app.sessions.active() ?: return false
        val root = activeRoot(session) ?: return false
        // Refresh immediately before each action; never trust a previous event's active state.
        val fresh = flatten(root)
        val selectors = WhatsAppSelectors.forRoute(session.route)
        val snapshot = selectors.snapshot(fresh.map { it.first })
        return if (action != Action.SEND || snapshot.canSend) {
            clickableNode(action, fresh, selectors)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        } else {
            false
        }
    }

    private fun clickableNode(
        action: Action,
        fresh: List<Pair<UiNode, AccessibilityNodeInfo>>,
        selectors: SelectorSet,
    ): AccessibilityNodeInfo? {
        val selector =
            when (action) {
                Action.TOGGLE -> selectors.viewOnceToggle
                Action.CONFIRM -> selectors.firstTimeDialogConfirm
                Action.SEND -> selectors.sendButton
            }
        val match = selector.find(fresh.map { it.first }) ?: return null
        var node: AccessibilityNodeInfo? = fresh.first { it.first === match.node }.second
        while (node != null && !node.isClickable) node = node.parent
        Log.i("Papelzinho", "action=$action selector=${match.strategy} elapsed=${SystemClock.elapsedRealtime()}")
        return node?.takeIf { it.isEnabled && it.isVisibleToUser }
    }

    private fun flatten(root: AccessibilityNodeInfo): List<Pair<UiNode, AccessibilityNodeInfo>> {
        val result = mutableListOf<Pair<UiNode, AccessibilityNodeInfo>>()
        var truncated = false
        val selectors = WhatsAppSelectors.forRoute(app.sessions.active()?.route ?: SendRoute.SHARE)
        val controlIds =
            selectors.viewOnceToggle.resourceIds + selectors.sendButton.resourceIds +
                selectors.firstTimeDialogConfirm.resourceIds

        fun visit(node: AccessibilityNodeInfo) {
            if (result.size >= SessionTiming.MAX_UI_NODES) {
                truncated = true
                return
            }
            val control = node.viewIdResourceName in controlIds
            result.add(
                UiNode(
                    resourceId = node.viewIdResourceName.orEmpty(),
                    description = if (control) node.contentDescription?.toString().orEmpty() else "",
                    stateDescription = if (control) node.stateDescription?.toString().orEmpty() else "",
                    text = "", // Conversation text is never inspected.
                    className = node.className?.toString().orEmpty(),
                    checkable = node.isCheckable,
                    checked = node.isChecked,
                    enabled = node.isEnabled && node.isVisibleToUser,
                ) to node,
            )
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        visit(root)
        return if (truncated) emptyList() else result
    }

    override fun onInterrupt() {
        app.sessions.active()?.let { app.sessions.release(it.token, SessionState.ABORTED) }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timer)
        scope.cancel()
        onInterrupt()
        super.onDestroy()
    }
}
