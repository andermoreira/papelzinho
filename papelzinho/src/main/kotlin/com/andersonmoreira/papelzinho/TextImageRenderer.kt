package com.andersonmoreira.papelzinho

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

class TextTooLong : IllegalArgumentException()

enum class NotePalette(
    val background: Int,
    val foreground: Int,
) {
    PAPER(Color.parseColor("#FFF8E2"), Color.parseColor("#2C2720")),
    NIGHT(Color.parseColor("#1B2330"), Color.parseColor("#F0F4F8")),
    SAGE(Color.parseColor("#DDECD8"), Color.parseColor("#233724")),
}

data class RenderStyle(
    val width: Int = NoteDimensions.WIDTH,
    val height: Int = NoteDimensions.STANDARD_HEIGHT,
    val palette: NotePalette = NotePalette.PAPER,
    val alignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
    val minSp: Int = 24,
    val maxSp: Int = 96,
)

class TextImageRenderer(
    private val createBitmap: (Int, Int) -> Bitmap = { width, height ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    },
) {
    fun render(
        text: String,
        style: RenderStyle,
    ): Result<ByteArray> =
        runCatching {
            val bitmap = draw(text, style)
            try {
                WipingOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }

    fun renderPreview(
        text: String,
        style: RenderStyle,
        maxWidthPx: Int,
    ): Result<Bitmap> =
        runCatching {
            require(maxWidthPx > 0)
            val ratio = maxWidthPx.toFloat() / style.width
            draw(
                text,
                style.copy(
                    width = maxWidthPx,
                    height = (style.height * ratio).toInt().coerceAtLeast(1),
                    minSp = (style.minSp * ratio).toInt().coerceAtLeast(1),
                    maxSp = (style.maxSp * ratio).toInt().coerceAtLeast(1),
                ),
            )
        }

    private fun draw(
        text: String,
        style: RenderStyle,
    ): Bitmap {
        require(style.width > 0 && style.height > 0 && style.minSp > 0 && style.maxSp >= style.minSp)
        val padding = (style.width * PADDING_RATIO).toInt()
        val availableWidth = style.width - padding * 2
        val availableHeight = style.height - padding * 2
        val paint =
            TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = style.palette.foreground
                typeface = Typeface.DEFAULT
            }

        fun layout(size: Int): StaticLayout {
            paint.textSize = size.toFloat()
            return StaticLayout.Builder
                .obtain(text, 0, text.length, paint, availableWidth)
                .setAlignment(style.alignment)
                .setIncludePad(true)
                .setLineSpacing(0f, LINE_SPACING)
                .build()
        }

        fun fits(candidate: StaticLayout): Boolean =
            candidate.height <= availableHeight &&
                (0 until candidate.lineCount).all { candidate.getLineWidth(it) <= availableWidth }
        if (!fits(layout(style.minSp))) throw TextTooLong()
        var low = style.minSp
        var high = style.maxSp
        var best = low
        while (low <= high) {
            val middle = low + ((high - low) ushr 1)
            if (fits(layout(middle))) {
                best = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        val content = layout(best)
        val bitmap = createBitmap(style.width, style.height)
        var completed = false
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(style.palette.background)
            canvas.translate(padding.toFloat(), (style.height - content.height) / 2f)
            content.draw(canvas)
            completed = true
            return bitmap
        } finally {
            if (!completed) bitmap.recycle()
        }
    }

    private companion object {
        const val PNG_QUALITY = 100
        const val PADDING_RATIO = 0.09f
        const val LINE_SPACING = 1.15f
    }

    private class WipingOutputStream : ByteArrayOutputStream() {
        override fun close() {
            buf.fill(0)
            reset()
            super.close()
        }
    }
}
