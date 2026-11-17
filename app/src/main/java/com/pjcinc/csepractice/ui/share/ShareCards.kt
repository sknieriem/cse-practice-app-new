package com.pjcinc.csepractice.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

/**
 * Celebration / progress cards drawn on the phone (no internet) and shared through the normal
 * Android share sheet — Messenger, Facebook, Viber, save to gallery, etc.
 */
object ShareCards {

    data class Card(
        val emoji: String,
        val headline: String,
        val lines: List<String>,
        val footer: String = "Reviewing with CSE Practice 📝"
    )

    fun pasadoMock(percent: Int, name: String, lang: String) = Card(
        "🎉",
        if (lang == "tl") "PASADO LEVEL!" else "PASSING LEVEL!",
        listOfNotNull(
            name.takeIf { it.isNotBlank() },
            if (lang == "tl") "$percent% sa isang timed CSE mock exam" else "$percent% on a timed CSE mock exam",
            if (lang == "tl") "Ang passing rating ay 80%. Tuloy ang review! 💪" else "The passing rating is 80%. Still reviewing! 💪"
        )
    )

    fun passedExam(level: String, name: String, lang: String) = Card(
        "🎓",
        "PASADO!",
        listOfNotNull(
            name.takeIf { it.isNotBlank() },
            if (lang == "tl") "Pumasa ako sa Civil Service Exam ($level)!" else "I passed the Civil Service Exam ($level)!",
            if (lang == "tl") "Salamat sa lahat ng sumuporta 🙏" else "Thank you to everyone who supported me 🙏"
        )
    )

    fun progress(
        name: String, estimate: Int?, weeklyGain: Int?, streak: Int, levelTitle: String, answered: Int, lang: String
    ) = Card(
        "📈",
        if (lang == "tl") "Ang aking CSE review" else "My CSE review",
        listOfNotNull(
            name.takeIf { it.isNotBlank() },
            estimate?.let { if (lang == "tl") "Estimated score: $it% (pasado: 80%)" else "Estimated score: $it% (passing: 80%)" },
            weeklyGain?.takeIf { it > 0 }?.let { if (lang == "tl") "+$it points ngayong linggo" else "+$it points this week" },
            if (streak > 0) "🔥 $streak-day streak · $levelTitle" else levelTitle,
            if (lang == "tl") "$answered tanong na nasagot" else "$answered questions answered"
        )
    )

    fun render(card: Card, size: Int = 1080): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), 0xFF13306B.toInt(), 0xFF1F57C3.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)
        // sun-yellow accents
        val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC928.toInt() }
        c.drawCircle(size * 0.88f, size * 0.12f, size * 0.22f, sun.apply { alpha = 40 })
        sun.alpha = 255

        val pad = size * 0.08f
        var y = size * 0.10f
        val emoji = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * 0.13f }
        c.drawText(card.emoji, pad, y + size * 0.11f, emoji)
        y += size * 0.17f

        y = drawWrapped(c, card.headline, pad, y, size - 2 * pad,
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFC928.toInt(); textSize = size * 0.10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
        y += size * 0.015f
        c.drawRoundRect(RectF(pad, y, pad + size * 0.22f, y + size * 0.014f), 8f, 8f, sun)
        y += size * 0.045f
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = size * 0.046f }
        card.lines.forEachIndexed { i, line ->
            y = drawWrapped(c, line, pad, y, size - 2 * pad, if (i == 0 && card.lines.size > 2)
                TextPaint(body).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = size * 0.055f } else body)
            y += size * 0.018f
        }
        val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = size * 0.035f }
        c.drawText(card.footer, pad, size * 0.93f, foot)
        return bmp
    }

    private fun drawWrapped(c: Canvas, text: String, x: Float, y: Float, width: Float, paint: TextPaint): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.1f).build()
        c.save(); c.translate(x, y); layout.draw(c); c.restore()
        return y + layout.height
    }

    fun saveToCache(context: Context, bmp: Bitmap, name: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    /** Opens the Android share sheet with the card image. */
    fun share(context: Context, card: Card, name: String) {
        val file = saveToCache(context, render(card), name)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
