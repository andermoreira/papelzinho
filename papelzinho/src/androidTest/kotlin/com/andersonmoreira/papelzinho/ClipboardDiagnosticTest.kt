package com.andersonmoreira.papelzinho

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
class ClipboardDiagnosticTest {
    @Test
    fun assistedCopyArmsClipboardRouteButOwnProviderReadDoesNotEnableAutomation() {
        val app = ApplicationProvider.getApplicationContext<Context>() as PapelzinhoApplication
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val bytes = byteArrayOf(4, 3, 2, 1)
                val session = app.clipboardDiagnostic.copyForSending(bytes, "com.whatsapp")
                try {
                    assertEquals(session, app.sessions.active())
                    assertEquals(SendRoute.CLIPBOARD, session.route)
                    assertNull(app.clipboardDiagnostic.sessions.active())
                    activity.contentResolver.openInputStream(app.imageUri(session.token))!!.use {
                        assertArrayEquals(bytes, it.readBytes())
                    }
                    assertFalse(session.targetHasRead)
                    app.sessions.release(session.token, SessionState.ABORTED)
                    assertArrayEquals(byteArrayOf(0, 0, 0, 0), bytes)
                    assertFalse(activity.getSystemService(ClipboardManager::class.java).hasPrimaryClip())
                } finally {
                    app.sessions.release(session.token, SessionState.ABORTED)
                }
            }
        }
    }

    @Test
    fun endingDiagnosticDoesNotClearAReplacementClip() {
        val app = ApplicationProvider.getApplicationContext<Context>() as PapelzinhoApplication
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val clipboard = activity.getSystemService(ClipboardManager::class.java)
                try {
                    app.clipboardDiagnostic.copy(byteArrayOf(1), "com.whatsapp")
                    clipboard.setPrimaryClip(ClipData.newPlainText("test", "replacement"))
                    app.clipboardDiagnostic.cancel()
                    assertEquals(
                        "replacement",
                        clipboard.primaryClip!!
                            .getItemAt(0)
                            .text
                            .toString(),
                    )
                } finally {
                    app.clipboardDiagnostic.cancel()
                    clipboard.clearPrimaryClip()
                }
            }
        }
    }

    @Test
    fun diagnosticCopiesReadableSensitiveUriWithoutArmingAutomationAndReleaseInvalidatesIt() {
        val app = ApplicationProvider.getApplicationContext<Context>() as PapelzinhoApplication
        app.sessions.active()?.let { app.sessions.release(it.token, SessionState.ABORTED) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val bytes = byteArrayOf(1, 2, 3, 4)
                val session = app.clipboardDiagnostic.copy(bytes, "com.whatsapp")
                try {
                    val clipboard = activity.getSystemService(ClipboardManager::class.java)
                    val clip = clipboard.primaryClip!!
                    val uri = clip.getItemAt(0).uri
                    assertEquals(app.imageUri(session.token), uri)
                    assertNull(clip.getItemAt(0).text)
                    assertTrue(clip.description.hasMimeType("image/png"))
                    assertTrue(clip.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
                    assertNull(app.sessions.active())
                    activity.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(bytes, it.readBytes()) }
                    app.clipboardDiagnostic.cancel()
                    assertArrayEquals(byteArrayOf(0, 0, 0, 0), bytes)
                    assertFalse(clipboard.hasPrimaryClip())
                    var rejected = false
                    try {
                        activity.contentResolver.openFileDescriptor(uri, "r")?.close()
                    } catch (_: FileNotFoundException) {
                        rejected = true
                    }
                    assertTrue(rejected)
                } finally {
                    app.clipboardDiagnostic.cancel()
                }
            }
        }
    }
}
