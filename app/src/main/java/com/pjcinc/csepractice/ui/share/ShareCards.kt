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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.pjcinc.csepractice.domain.Game
import java.io.File

/**
 * Celebration / progress cards drawn on the phone (no internet) and shared through the normal
 * Android share sheet — Messenger, Facebook, Viber, TikTok, save to gallery, etc.
 *
 * Every card carries the app name, a one-line invite and an offline QR code to the Play Store page,
 * so a family member who sees the post can find the app. Only the user's own achievement is shown —
 * never a rank or a comparison — and the only personal data is the first name the user chose to show.
 */
object ShareCards {

    const val APP_NAME = "CSE Practice"

    /** Keep in sync with defaultConfig.applicationId in app/build.gradle.kts (a unit test checks it). */
    const val STORE_PACKAGE = "com.pjcinc.csepractice"
    const val STORE_URL = "https://play.google.com/store/apps/details?id=$STORE_PACKAGE"

    enum class Format(val width: Int, val height: Int) {
        /** Facebook / Messenger feed post. */
        SQUARE(1080, 1080),
        /** 9:16 — TikTok, Facebook / Instagram Stories. */
        STORY(1080, 1920)
    }

    data class Card(
        val emoji: String,
        val headline: String,
        val lines: List<String>,
        /** "en" or "tl" — picks the language of the branding line. */
        val lang: String = "en"
    )

    private fun t(lang: String, en: String, tl: String) = if (lang == "tl") tl else en
    private fun nameLine(name: String) = name.trim().takeIf { it.isNotEmpty() }

    // ---- the cards ---------------------------------------------------------------------------

    fun pasadoMock(percent: Int, name: String, lang: String) = Card(
        "🎉",
        t(lang, "PASSING LEVEL!", "PASADO LEVEL!"),
        listOfNotNull(
            nameLine(name),
            t(lang, "$percent% on a timed CSE mock exam", "$percent% sa isang timed CSE mock exam"),
            t(lang, "The passing rating is 80%. Still reviewing! 💪", "Ang passing rating ay 80%. Tuloy ang review! 💪")
        ), lang
    )

    fun passedExam(level: String, name: String, lang: String) = Card(
        "🎓",
        "PASADO!",
        listOfNotNull(
            nameLine(name),
            t(lang, "I passed the Civil Service Exam ($level)!", "Pumasa ako sa Civil Service Exam ($level)!"),
            t(lang, "Thank you to everyone who supported me 🙏", "Salamat sa lahat ng sumuporta 🙏")
        ), lang
    )

    fun progress(
        name: String, estimate: Int?, weeklyGain: Int?, streak: Int, levelTitle: String, answered: Int, lang: String
    ) = Card(
        "📈",
        t(lang, "My CSE review", "Ang aking CSE review"),
        listOfNotNull(
            nameLine(name),
            estimate?.let { t(lang, "Estimated score: $it% (passing: 80%)", "Estimated score: $it% (pasado: 80%)") },
            weeklyGain?.takeIf { it > 0 }?.let { t(lang, "+$it points this week", "+$it points ngayong linggo") },
            if (streak > 0) "🔥 $streak-day streak · $levelTitle" else levelTitle,
            t(lang, "$answered questions answered", "$answered tanong na nasagot")
        ), lang
    )

    fun levelUp(level: Int, title: String, name: String, lang: String) = Card(
        "🌟",
        "LEVEL UP!",
        listOfNotNull(
            nameLine(name),
            t(lang, "I'm now Level $level · $title", "Level $level na ako · $title"),
            t(lang, "One step closer to passing the CSE 💪", "Isang hakbang palapit sa pagpasa sa CSE 💪")
        ), lang
    )

    fun badge(b: Game.Badge, name: String, lang: String) = Card(
        b.emoji,
        t(lang, "New badge!", "Bagong badge!"),
        listOfNotNull(
            nameLine(name),
            b.title,
            b.description,
            t(lang, "Hard work, one day at a time 🙌", "Sipag at tiyaga, araw-araw 🙌")
        ), lang
    )

    /** Streak days that get their own share moment. */
    val STREAK_MILESTONES = setOf(7, 30, 100)

    fun streak(days: Int, name: String, lang: String) = Card(
        "🔥",
        t(lang, "$days-DAY STREAK!", "$days ARAW NA STREAK!"),
        listOfNotNull(
            nameLine(name),
            t(lang, "$days days in a row of CSE review", "$days araw na tuloy-tuloy ang CSE review ko"),
            t(lang, "Small steps, every single day 💪", "Paunti-unti, pero araw-araw 💪")
        ), lang
    )

    /** Same bar as the "Perfect!" badge: 100% on 10 or more questions. */
    fun isPerfect(correct: Int, total: Int) = total >= 10 && correct == total

    fun perfect(total: Int, name: String, lang: String) = Card(
        "⭐",
        t(lang, "PERFECT SCORE!", "PERFECT SCORE!"),
        listOfNotNull(
            nameLine(name),
            t(lang, "$total out of $total correct — 100%", "$total sa $total ang tama — 100%"),
            t(lang, "All that practice is paying off 🙌", "Nagbubunga na ang sipag ko 🙌")
        ), lang
    )

    fun firstMock(name: String, lang: String) = Card(
        "⏱️",
        t(lang, "First mock exam done!", "Unang mock exam, tapos!"),
        listOfNotNull(
            nameLine(name),
            t(lang, "I finished my first timed CSE mock exam", "Natapos ko ang una kong timed CSE mock exam"),
            t(lang, "The road to PASADO has started 💪", "Simula na ng daan papuntang PASADO 💪")
        ), lang
    )

    // ---- layout ------------------------------------------------------------------------------

    /** One block of text, already broken into lines, at its place on the card. */
    class Placed(val x: Float, val y: Float, val layout: StaticLayout) {
        val bottom get() = y + layout.height
    }

    class Plan(
        val width: Int,
        val height: Int,
        /** emoji, headline, body lines */
        val content: List<Placed>,
        val contentTop: Float,
        val contentBottom: Float,
        val accentBar: RectF,
        val panel: RectF,
        /** app name, invite line, "scan" line */
        val brand: List<Placed>,
        val qr: RectF
    ) {
        val allText get() = content + brand
    }

    private val BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private const val NAVY = 0xFF13306B.toInt()
    private const val BLUE = 0xFF1F57C3.toInt()
    private const val SUN = 0xFFFFC928.toInt()

    /** Shrinks [paint] until its widest word fits [width], so no word is ever split mid-word. */
    private fun fitWords(text: String, paint: TextPaint, width: Float): TextPaint {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        while (paint.textSize > 8f && words.any { paint.measureText(it) > width }) paint.textSize *= 0.94f
        return paint
    }

    private fun layout(text: String, paint: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout {
        fitWords(text, paint, width)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(align).setLineSpacing(0f, 1.1f).setIncludePad(true).build()
    }

    fun plan(card: Card, format: Format = Format.SQUARE, width: Int = format.width): Plan {
        val s = width / 1080f
        val w = width.toFloat()
        val h = format.height * s
        val story = format == Format.STORY
        val pad = 86f * s

        // ---- branding panel along the bottom (stays clear of Stories' reply bar) ----
        val qrSize = (if (story) 300f else 220f) * s
        val inner = 30f * s
        val panelBottom = h - (if (story) 190f else 56f) * s
        val panel = RectF(pad * 0.7f, panelBottom - qrSize - 2 * inner, w - pad * 0.7f, panelBottom)
        val qr = RectF(panel.right - inner - qrSize, panel.top + inner, panel.right - inner, panel.top + inner + qrSize)
        val textW = qr.left - panel.left - 2 * inner
        val bx = panel.left + inner
        val nameP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; textSize = (if (story) 64f else 54f) * s; typeface = BOLD }
        val inviteP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B3A55.toInt(); textSize = (if (story) 40f else 34f) * s }
        val scanP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE; textSize = (if (story) 34f else 29f) * s; typeface = BOLD }
        val brandTexts = listOf(
            APP_NAME to nameP,
            t(card.lang, "Practice free for the Civil Service Exam", "Libreng practice para sa Civil Service Exam") to inviteP,
            t(card.lang, "Scan to get the app 📲", "I-scan para makuha ang app 📲") to scanP
        )
        var brandScale = 1f
        var brand: List<Placed>
        while (true) {
            var by = 0f
            brand = brandTexts.mapIndexed { i, (text, p) ->
                val l = layout(text, TextPaint(p).apply { textSize *= brandScale }, textW)
                Placed(bx, by, l).also { by += l.height + (if (i == 0) 6f else 14f) * s }
            }
            val total = brand.last().bottom
            if (total <= panel.height() - 2 * inner || brandScale < 0.6f) {
                val top = panel.top + (panel.height() - total) / 2f   // centred beside the QR
                brand = brand.map { Placed(it.x, it.y + top, it.layout) }
                break
            }
            brandScale *= 0.93f
        }

        // ---- celebration text: shrink everything together until it fits above the panel ----
        val contentTop = (if (story) 230f else 70f) * s
        val contentBottom = panel.top - 40f * s
        val textWidth = w - 2 * pad
        var k = 1f
        var content: List<Placed>
        var bar: RectF
        while (true) {
            val f = k * s * (if (story) 1.12f else 1f)
            val items = mutableListOf<Placed>()
            var y = 0f
            val emoji = layout(card.emoji, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 130f * f }, textWidth)
            items += Placed(pad, y, emoji); y += emoji.height + 10f * f
            val head = layout(card.headline, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = SUN; textSize = 104f * f; typeface = BOLD }, textWidth)
            items += Placed(pad, y, head); y += head.height + 16f * f
            bar = RectF(pad, y, pad + 240f * f, y + 15f * f); y += 15f * f + 40f * f
            val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 48f * f }
            card.lines.forEachIndexed { i, line ->
                val p = if (i == 0 && card.lines.size > 2) TextPaint(body).apply { typeface = BOLD; textSize = 58f * f } else TextPaint(body)
                val l = layout(line, p, textWidth)
                items += Placed(pad, y, l); y += l.height + 20f * f
            }
            val total = y - 20f * f
            val avail = contentBottom - contentTop
            if (total <= avail || k < 0.45f) {
                // Square: top-aligned. Story: centred in the tall space, slightly high.
                val off = contentTop + if (story) ((avail - total) * 0.42f).coerceAtLeast(0f) else 0f
                content = items.map { Placed(it.x, it.y + off, it.layout) }
                bar = RectF(bar.left, bar.top + off, bar.right, bar.bottom + off)
                break
            }
            k *= 0.94f
        }
        return Plan(width, h.toInt(), content, contentTop, contentBottom, bar, panel, brand, qr)
    }

    // ---- QR (zxing core, offline) ------------------------------------------------------------

    private val qrMatrix: BitMatrix by lazy {
        QRCodeWriter().encode(
            STORE_URL, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        )
    }

    private fun drawQr(c: Canvas, r: RectF) {
        val m = qrMatrix
        val cell = r.width() / m.width
        val dark = Paint().apply { color = 0xFF0B1B3F.toInt() }
        c.drawRect(r, Paint().apply { color = 0xFFFFFFFF.toInt() })
        for (yy in 0 until m.height) for (xx in 0 until m.width) {
            if (m.get(xx, yy)) c.drawRect(r.left + xx * cell, r.top + yy * cell, r.left + (xx + 1) * cell + 0.5f, r.top + (yy + 1) * cell + 0.5f, dark)
        }
    }

    // ---- drawing -----------------------------------------------------------------------------

    fun render(card: Card, format: Format = Format.SQUARE, width: Int = format.width): Bitmap {
        val p = plan(card, format, width)
        val w = p.width.toFloat()
        val h = p.height.toFloat()
        val s = p.width / 1080f
        val bmp = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, w, h, NAVY, BLUE, Shader.TileMode.CLAMP) })
        // warm sun glow + a few soft confetti dots: celebration, not a report
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUN; alpha = 40 }
        c.drawCircle(w * 0.88f, w * 0.12f, w * 0.22f, glow)
        if (format == Format.STORY) c.drawCircle(w * 0.08f, h * 0.62f, w * 0.16f, Paint(glow).apply { alpha = 22 })
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        listOf(0.70f to 0.30f to SUN, 0.82f to 0.40f to 0xFFE5484D.toInt(), 0.92f to 0.26f to 0xFF7BE3A6.toInt(), 0.62f to 0.20f to 0xFFFFFFFF.toInt())
            .forEach { (pos, col) -> dot.color = col; dot.alpha = 150; c.drawCircle(w * pos.first, p.contentTop + w * pos.second * 0.3f, 9f * s, dot) }

        p.content.forEach { pl -> c.save(); c.translate(pl.x, pl.y); pl.layout.draw(c); c.restore() }
        c.drawRoundRect(p.accentBar, 8f * s, 8f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUN })

        c.drawRoundRect(p.panel, 34f * s, 34f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        c.drawRoundRect(RectF(p.panel.left, p.panel.top, p.panel.left + 14f * s, p.panel.bottom), 7f * s, 7f * s,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUN })
        p.brand.forEach { pl -> c.save(); c.translate(pl.x, pl.y); pl.layout.draw(c); c.restore() }
        drawQr(c, p.qr)
        return bmp
    }

    fun saveToCache(context: Context, bmp: Bitmap, name: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    /** Opens the Android share sheet with the card image. */
    fun share(context: Context, card: Card, name: String, format: Format = Format.SQUARE) {
        val tag = if (format == Format.STORY) "${name}_story" else name
        val file = saveToCache(context, render(card, format), tag)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
