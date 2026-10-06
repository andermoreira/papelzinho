package com.andersonmoreira.papelzinho

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityTest {
    @Test
    fun sharedTextStaysInViewModelAndWindowIsSecure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Ephemeral note")
            }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                assertEquals("Ephemeral note", ViewModelProvider(activity)[NoteViewModel::class.java].text)
                assertFalse(activity.intent.hasExtra(Intent.EXTRA_TEXT))
                val state = Bundle()
                InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity, state)
                val parcel = Parcel.obtain()
                try {
                    parcel.writeBundle(state)
                    val serialized = parcel.marshall().toList()
                    val marker = "Ephemeral note".toByteArray(Charsets.UTF_16LE).toList()
                    assertFalse(serialized.windowed(marker.size).any { it == marker })
                } finally {
                    parcel.recycle()
                }
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("Ephemeral note", ViewModelProvider(activity)[NoteViewModel::class.java].text)
            }
        }
    }
}
