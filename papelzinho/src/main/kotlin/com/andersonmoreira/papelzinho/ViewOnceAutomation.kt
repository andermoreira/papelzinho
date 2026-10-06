package com.andersonmoreira.papelzinho

enum class Action { TOGGLE, CONFIRM, SEND }

enum class AutomationState { WAITING_PREVIEW, PREVIEW_FOUND, VERIFY_TOGGLE, AWAIT_HANDOFF, DONE, ABORT }

data class PreviewSnapshot(
    val preview: Boolean = false,
    val toggle: Boolean = false,
    val active: Boolean = false,
    val send: Boolean = false,
    val knownDialog: Boolean = false,
    val unknownDialog: Boolean = false,
) {
    val unobstructed get() = !knownDialog && !unknownDialog
    val canSend get() = preview && toggle && active && send && unobstructed
    private val noPreviewControls get() = !preview && !toggle && !send
    val outsidePreview get() = noPreviewControls && unobstructed
}

class ViewOnceAutomation(
    private val store: SessionStore,
    private val clock: () -> Long,
    private val click: (Action) -> Boolean,
    private val completed: (SessionState) -> Unit = {},
) {
    var state = AutomationState.WAITING_PREVIEW
        private set
    private var token: String? = null
    private var startedAt = 0L
    private var sentAt = 0L
    private var confirmed = false
    private var clipboardHandoffObserved = false

    fun tick() {
        val session = store.active() ?: return
        if (token != session.token) return
        when {
            state == AutomationState.AWAIT_HANDOFF && clock() - sentAt >= SessionTiming.HANDOFF_TIMEOUT_MS ->
                finish(
                    SessionState.DONE,
                )
            state == AutomationState.PREVIEW_FOUND && clock() - startedAt >= SessionTiming.TOGGLE_TIMEOUT_MS ->
                finish(
                    SessionState.ABORTED,
                )
            state == AutomationState.VERIFY_TOGGLE && clock() - startedAt >= SessionTiming.PREVIEW_TIMEOUT_MS ->
                finish(
                    SessionState.ABORTED,
                )
        }
    }

    fun onSnapshot(
        packageName: String,
        snapshot: PreviewSnapshot,
    ) {
        val session = store.active() ?: return
        if (session.targetPackage != packageName) return
        if (token != session.token) {
            token = session.token
            state = AutomationState.WAITING_PREVIEW
            confirmed = false
            clipboardHandoffObserved = false
        }
        if (!clipboardReady(session, snapshot)) return
        tick()
        if (store.active()?.token != token) return
        when (state) {
            AutomationState.WAITING_PREVIEW -> {
                if (snapshot.preview && store.markInProgress(session.token)) {
                    startedAt = clock()
                    state = AutomationState.PREVIEW_FOUND
                    handlePreview(snapshot)
                }
            }
            AutomationState.PREVIEW_FOUND, AutomationState.VERIFY_TOGGLE -> handlePreview(snapshot)
            AutomationState.AWAIT_HANDOFF -> if (!snapshot.preview) finish(SessionState.DONE)
            AutomationState.DONE, AutomationState.ABORT -> Unit
        }
    }

    private fun clipboardReady(
        session: SendSession,
        snapshot: PreviewSnapshot,
    ): Boolean {
        if (session.route != SendRoute.CLIPBOARD || session.targetHasRead) return true
        if (snapshot.outsidePreview) clipboardHandoffObserved = true
        return session.keyboardHasRead && clipboardHandoffObserved
    }

    private fun handlePreview(snapshot: PreviewSnapshot) {
        when {
            snapshot.unknownDialog -> finish(SessionState.ABORTED)
            snapshot.knownDialog -> confirmDialog()
            !snapshot.preview -> finish(SessionState.ABORTED)
            snapshot.canSend -> sendVerified()
            state == AutomationState.PREVIEW_FOUND && snapshot.toggle -> {
                if (click(Action.TOGGLE)) state = AutomationState.VERIFY_TOGGLE else finish(SessionState.ABORTED)
            }
        }
    }

    private fun confirmDialog() {
        if (confirmed || !click(Action.CONFIRM)) {
            finish(SessionState.ABORTED)
        } else {
            confirmed = true
            state = AutomationState.VERIFY_TOGGLE
        }
    }

    private fun sendVerified() {
        val currentToken = token ?: return
        if (click(Action.SEND)) {
            store.markSent(currentToken)
            sentAt = clock()
            state = AutomationState.AWAIT_HANDOFF
        } else {
            finish(SessionState.ABORTED)
        }
    }

    private fun finish(terminal: SessionState) {
        val currentToken = token ?: return
        state = if (terminal == SessionState.DONE) AutomationState.DONE else AutomationState.ABORT
        store.release(currentToken, terminal)
        completed(terminal)
    }
}
