package com.andersonmoreira.papelzinho

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentTest {
    @Test
    fun grantsOnlyReadToChosenPackageAndUsesSameUriInClipData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context as PapelzinhoApplication
        val session = app.sessions.arm(byteArrayOf(1), "com.whatsapp")
        try {
            val intent = WhatsAppShareIntent.create(app, session)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertEquals("com.whatsapp", intent.`package`)
            assertEquals(
                app.imageUri(session.token),
                intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM),
            )
            assertEquals(app.imageUri(session.token), intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } finally {
            app.sessions.release(session.token, SessionState.ABORTED)
        }
    }
}
