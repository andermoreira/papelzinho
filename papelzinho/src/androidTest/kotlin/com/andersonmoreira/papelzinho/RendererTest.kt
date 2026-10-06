package com.andersonmoreira.papelzinho

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RendererTest {
    @Test
    fun dimensionsEmojiAndRecycling() {
        var allocated: Bitmap? = null
        val renderer =
            TextImageRenderer { width, height ->
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { allocated = it }
            }
        val bytes = renderer.render("Hello 👋🏽\nOlá 🌻", RenderStyle()).getOrThrow()
        assertTrue(allocated!!.isRecycled)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(1080, decoded.width)
            assertEquals(1350, decoded.height)
        } finally {
            decoded.recycle()
            bytes.fill(0)
        }
    }

    @Test
    fun oversizedTextIsNeverTruncated() {
        val result = TextImageRenderer().render("a\n".repeat(500), RenderStyle())
        assertTrue(result.exceptionOrNull() is TextTooLong)
    }

    @Test
    fun portraitAndPreviewAreSeparate() {
        val renderer = TextImageRenderer()
        val bytes = renderer.render("hello", RenderStyle(height = 1920)).getOrThrow()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val preview = renderer.renderPreview("hello", RenderStyle(height = 1920), 216).getOrThrow()
        try {
            assertEquals(1920, decoded.height)
            assertEquals(216, preview.width)
            assertEquals(384, preview.height)
            assertNotSame(decoded, preview)
        } finally {
            decoded.recycle()
            preview.recycle()
            bytes.fill(0)
        }
    }
}
