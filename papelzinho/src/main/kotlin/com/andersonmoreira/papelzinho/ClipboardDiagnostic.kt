package com.andersonmoreira.papelzinho

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.os.SystemClock
import android.util.Log

class ClipboardDiagnostic(
    private val app: PapelzinhoApplication,
) {
    private var pendingCleanup: Uri? = null
    val sessions =
        SessionStore(SystemClock::elapsedRealtime) { token ->
            val uri = app.imageUri(token)
            app.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            released(token)
        }

    fun copy(
        bytes: ByteArray,
        targetPackage: String,
    ): SendSession {
        app.sessions.active()?.let { app.sessions.release(it.token, SessionState.ABORTED) }
        val session = sessions.arm(bytes, targetPackage)
        return writeClip(session, sessions)
    }

    fun copyForSending(
        bytes: ByteArray,
        targetPackage: String,
    ): SendSession {
        cancel()
        val session = app.sessions.arm(bytes, targetPackage, SendRoute.CLIPBOARD)
        Log.i("Papelzinho", "clipboard=armed elapsed=${SystemClock.elapsedRealtime()}")
        return writeClip(session, app.sessions)
    }

    private fun writeClip(
        session: SendSession,
        store: SessionStore,
    ): SendSession {
        val clip = ClipData.newUri(app.contentResolver, "papelzinho", app.imageUri(session.token))
        clip.description.extras =
            PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        var copied = false
        try {
            app.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            copied = true
        } finally {
            if (!copied) store.release(session.token, SessionState.ABORTED)
        }
        return session
    }

    fun cancel() {
        sessions.active()?.let { sessions.release(it.token, SessionState.ABORTED) }
        cleanupClipboard()
    }

    fun released(token: String) {
        Log.i("Papelzinho", "clipboard=uri_released elapsed=${SystemClock.elapsedRealtime()}")
        pendingCleanup = app.imageUri(token)
        cleanupClipboard()
    }

    // Background clipboard reads can be denied. The URI is revoked regardless;
    // retry removing our stale clip when the activity returns to the foreground.
    fun cleanupClipboard() {
        val uri = pendingCleanup ?: return
        val manager = app.getSystemService(ClipboardManager::class.java)
        val clip = manager.primaryClip ?: return
        if ((0 until clip.itemCount).any { clip.getItemAt(it).uri == uri }) {
            manager.clearPrimaryClip()
        }
        pendingCleanup = null
    }
}
