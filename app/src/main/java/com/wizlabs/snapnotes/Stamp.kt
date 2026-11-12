package com.wizlabs.snapnotes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Burns the note into the photo: the header band across the top (header + date/time) and the
 * notes band across the bottom. Everything is sized from the image width, so the full photo and
 * the small live preview look identical.
 */
object Stamp {
    private const val BAND = 0xC70B0D10.toInt()      // Wiz black, ~78% opaque
    private const val YELLOW = 0xFFFFC400.toInt()
    private const val WHITE = 0xFFF4F5F7.toInt()
    private const val MUTED = 0xFFB4BAC4.toInt()

    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    fun timeLabel(ms: Long): String =
        SimpleDateFormat("d MMM yyyy · h:mm a", Locale.ENGLISH).format(Date(ms))

    fun draw(canvas: Canvas, w: Int, h: Int, header: String, body: String, timeMs: Long) {
        val u = minOf(w, h) / 100f
        val pad = 3.6f * u
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // ---- Header band (top) ------------------------------------------------------------
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE; typeface = bold; textSize = 4.6f * u
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = YELLOW; typeface = bold; textSize = 2.8f * u; letterSpacing = 0.02f
        }
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED; typeface = bold; textSize = 2.3f * u; letterSpacing = 0.12f
        }
        val textLeft = pad + 1.6f * u
        val textWidth = (w - textLeft - pad).toInt().coerceAtLeast(1)
        val headerLayout = layout(header.trim().ifEmpty { " " }, headerPaint, textWidth, 2)
        val metaH = metaPaint.fontSpacing
        val topH = pad + headerLayout.height + 1.1f * u + metaH + pad * 0.85f

        fill.color = BAND
        canvas.drawRect(0f, 0f, w.toFloat(), topH, fill)
        fill.color = YELLOW
        canvas.drawRoundRect(RectF(pad, pad, pad + 0.8f * u, topH - pad * 0.85f), 0.4f * u, 0.4f * u, fill)

        canvas.save()
        canvas.translate(textLeft, pad)
        headerLayout.draw(canvas)
        canvas.restore()

        val metaBaseline = pad + headerLayout.height + 1.1f * u - metaPaint.ascent()
        canvas.drawText(timeLabel(timeMs), textLeft, metaBaseline, metaPaint)
        val brand = "WIZ SNAP NOTES"
        canvas.drawText(brand, w - pad - brandPaint.measureText(brand), metaBaseline, brandPaint)

        // ---- Notes band (bottom) ----------------------------------------------------------
        val notes = body.trim()
        if (notes.isEmpty()) return
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = YELLOW; typeface = bold; textSize = 2.4f * u; letterSpacing = 0.14f
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; textSize = 3.4f * u }
        val bodyWidth = (w - pad * 2).toInt().coerceAtLeast(1)
        // Long notes wrap and the band grows, up to 45% of the photo; beyond that they end in "…".
        val labelH = labelPaint.fontSpacing + 0.8f * u
        val maxBodyH = h * 0.45f - pad * 2 - labelH
        val maxLines = (maxBodyH / bodyPaint.fontSpacing).toInt().coerceAtLeast(1)
        val bodyLayout = layout(notes, bodyPaint, bodyWidth, maxLines)
        val bandH = pad + labelH + bodyLayout.height + pad
        val top = h - bandH

        fill.color = BAND
        canvas.drawRect(0f, top, w.toFloat(), h.toFloat(), fill)
        fill.color = YELLOW
        canvas.drawRect(0f, top, w.toFloat(), top + 0.45f * u, fill)
        canvas.drawText("NOTES", pad, top + pad - labelPaint.ascent(), labelPaint)
        canvas.save()
        canvas.translate(pad, top + pad + labelH)
        bodyLayout.draw(canvas)
        canvas.restore()
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
}
