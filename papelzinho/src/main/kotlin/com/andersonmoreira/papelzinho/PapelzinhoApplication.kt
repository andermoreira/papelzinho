package com.andersonmoreira.papelzinho

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class PapelzinhoApplication : Application() {
    val clipboardDiagnostic by lazy { ClipboardDiagnostic(this) }
    val sessions by lazy {
        SessionStore(SystemClock::elapsedRealtime) { token ->
            revokeUriPermission(imageUri(token), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipboardDiagnostic.released(token)
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val expiry =
        object : Runnable {
            override fun run() {
                sessions.active()
                clipboardDiagnostic.sessions.active()
                handler.postDelayed(this, SessionTiming.EXPIRY_POLL_MS)
            }
        }

    override fun onCreate() {
        super.onCreate()
        handler.post(expiry)
    }

    fun imageUri(token: String): Uri = Uri.parse("content://$packageName.images/$token.png")

    fun <T> withImageBytes(
        token: String,
        block: (ByteArray) -> T,
    ): T? = sessions.withBytes(token, block) ?: clipboardDiagnostic.sessions.withBytes(token, block)
}
