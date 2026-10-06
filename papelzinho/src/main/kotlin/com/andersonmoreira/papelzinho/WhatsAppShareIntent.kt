package com.andersonmoreira.papelzinho

import android.content.ClipData
import android.content.Intent

object WhatsAppShareIntent {
    fun create(
        app: PapelzinhoApplication,
        session: SendSession,
    ): Intent {
        val uri = app.imageUri(session.token)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(app.contentResolver, "papelzinho", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(session.targetPackage)
        }
    }
}
