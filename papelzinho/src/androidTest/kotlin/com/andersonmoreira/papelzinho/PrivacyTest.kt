package com.andersonmoreira.papelzinho

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun apkDoesNotDeclareInternetAndNamesAreLocalized() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        assertEquals(setOf("android.permission.POST_NOTIFICATIONS"), info.requestedPermissions.orEmpty().toSet())
        for ((locale, expected) in listOf("pt-BR" to "Papelzinho", "en-US" to "Pass the Note")) {
            val config = Configuration(context.resources.configuration)
            config.setLocales(LocaleList.forLanguageTags(locale))
            assertEquals(expected, context.createConfigurationContext(config).getString(R.string.app_name))
        }
    }

    @Test
    fun simulatedSendCreatesNoContentFiles() {
        val app = context.applicationContext as PapelzinhoApplication
        val dirs =
            listOfNotNull(
                context.filesDir,
                context.cacheDir,
                context.externalCacheDir,
                context.getExternalFilesDir(null),
            )

        fun files() =
            dirs
                .flatMap { dir ->
                    dir
                        .walkTopDown()
                        .filter { it.isFile }
                        .map { it.absolutePath }
                        .toList()
                }.toSet()
        val before = files()
        val bytes = TextImageRenderer().render("Privacy test", RenderStyle()).getOrThrow()
        val session = app.sessions.arm(bytes, "com.whatsapp")
        var now = 0L
        val engine =
            ViewOnceAutomation(app.sessions, { now }, { action ->
                if (action == Action.SEND) {
                    context.contentResolver.openInputStream(app.imageUri(session.token))!!.use {
                        assertArrayEquals(bytes, it.readBytes())
                    }
                }
                true
            })
        engine.onSnapshot("com.whatsapp", PreviewSnapshot(preview = true, toggle = true, active = true, send = true))
        engine.onSnapshot("com.whatsapp", PreviewSnapshot())
        assertEquals(SessionState.DONE, session.state)
        assertNull(app.sessions.bytesFor(session.token))
        assertEquals(before, files())
    }
}
