package com.pjcinc.csepractice

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.pjcinc.csepractice.domain.Game
import com.pjcinc.csepractice.ui.share.ShareCards
import com.pjcinc.csepractice.ui.share.ShareCards.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Share cards at both sizes: dimensions, everything fits, no word split mid-word, QR scans to the store page. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShareCardsTest {

    private fun allCards(name: String) = listOf(
        "pasado" to ShareCards.pasadoMock(84, name),
        "passed" to ShareCards.passedExam("Subprofessional", name),
        "progress" to ShareCards.progress(name, 78, 4, 12, "Masipag na Reviewer", 640),
        "level_up" to ShareCards.levelUp(5, Game.titleFor(5), name),
        "badge" to ShareCards.badge(Game.badge("review_10")!!, name),
        "badge_long" to ShareCards.badge(Game.badge("master_gi")!!, name),
        "streak_7" to ShareCards.streak(7, name),
        "streak_100" to ShareCards.streak(100, name),
        "perfect" to ShareCards.perfect(20, name),
        "first_mock" to ShareCards.firstMock(name)
    )

    private val names = listOf("", "Pearl", "Maria Concepcion Dela Cruz-Villanueva")

    @Test
    fun renders_square_1080_and_story_1080x1920() {
        val card = ShareCards.levelUp(3, Game.titleFor(3), "Pearl")
        ShareCards.render(card, Format.SQUARE).let { assertEquals(1080, it.width); assertEquals(1080, it.height) }
        ShareCards.render(card, Format.STORY).let { assertEquals(1080, it.width); assertEquals(1920, it.height) }
        // the on-screen preview size keeps the same aspect ratio
        ShareCards.render(card, Format.STORY, 540).let { assertEquals(540, it.width); assertEquals(960, it.height) }
    }

    @Test
    fun every_card_fits_and_never_breaks_a_word() {
        val problems = mutableListOf<String>()
        for (name in names) for ((id, card) in allCards(name)) for (f in Format.entries) {
            val p = ShareCards.plan(card, f)
            val where = "$id/${f.name}/name='${name}'"
            p.content.forEach { if (it.bottom > p.contentBottom + 1f) problems += "$where: text runs into the branding panel" }
            p.brand.forEach { if (it.bottom > p.panel.bottom || it.y < p.panel.top) problems += "$where: brand text outside panel" }
            if (p.brand.any { it.x + it.layout.width > p.qr.left }) problems += "$where: brand text under the QR"
            for (pl in p.allText) {
                val l = pl.layout
                val text = l.text.toString()
                for (line in 0 until l.lineCount - 1) {
                    val end = l.getLineEnd(line)
                    if (end <= 0 || end >= text.length) continue
                    if (text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit())
                        problems += "$where: \"$text\" breaks at ${text.substring(maxOf(0, end - 6), end)}|${text.substring(end)}"
                }
            }
        }
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun every_card_carries_the_branding() {
        for (f in Format.entries) {
            val brand = ShareCards.plan(ShareCards.streak(30, ""), f).brand.map { it.layout.text.toString() }
            assertEquals(listOf(ShareCards.APP_NAME, "Practice app for the Civil Service Exam", "Scan to get the app 📲"), brand)
        }
    }

    @Test
    fun qr_scans_to_the_play_store_page_in_both_formats() {
        for (f in Format.entries) {
            val bmp = ShareCards.render(ShareCards.perfect(10, "Pearl"), f)
            val qr = ShareCards.plan(ShareCards.perfect(10, "Pearl"), f).qr
            val crop = Bitmap.createBitmap(bmp, qr.left.toInt() - 20, qr.top.toInt() - 20, qr.width().toInt() + 40, qr.height().toInt() + 40)
            val px = IntArray(crop.width * crop.height).also { crop.getPixels(it, 0, crop.width, 0, 0, crop.width, crop.height) }
            val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(crop.width, crop.height, px))))
            assertEquals(ShareCards.STORE_URL, result.text)
        }
    }

    @Test
    fun store_url_uses_the_apps_application_id() {
        val gradle = File("build.gradle.kts").readText()
        val id = Regex("""applicationId\s*=\s*"([^"]+)"""").find(gradle)!!.groupValues[1]
        assertEquals("https://play.google.com/store/apps/details?id=$id", ShareCards.STORE_URL)
    }

    @Test
    fun name_is_optional_and_nothing_else_personal() {
        for ((_, card) in allCards("")) assertFalse(card.lines.any { it.isBlank() })
        val withName = ShareCards.streak(7, "  Pearl ")
        assertEquals("Pearl", withName.lines.first())
        assertFalse(ShareCards.streak(7, "").lines.contains("Pearl"))
    }

    /** Cards are English only (the CSE is in English; many users speak Bisaya) and never say "free" (paid app). */
    @Test
    fun cards_are_english_and_never_promise_free() {
        val taglish = Regex("""(?i)\b(ako|ang|ng|sa|ko|araw|tapos|pumasa|salamat|libreng|bagong|ngayong|tanong|i-scan|makuha|unang)\b""")
        for ((id, card) in allCards("")) for (f in Format.entries) {
            val text = ShareCards.plan(card, f).allText.joinToString(" ") { it.layout.text.toString() }
            assertFalse("$id has Taglish: $text", taglish.containsMatchIn(text))
            assertFalse("$id says free: $text", Regex("""(?i)\bfree\b|libre""").containsMatchIn(text))
        }
    }

    @Test
    fun streak_moments_and_perfect_rule() {
        assertEquals(setOf(7, 30, 100), ShareCards.STREAK_MILESTONES)
        assertTrue(ShareCards.isPerfect(10, 10))
        assertFalse(ShareCards.isPerfect(5, 5))   // same bar as the "Perfect!" badge
        assertFalse(ShareCards.isPerfect(19, 20))
    }

    /** Samples for Steve to look at: dist/share-samples/ (repo root). */
    @Test
    fun save_sample_pngs() {
        val dir = File("../dist/share-samples").apply { mkdirs() }
        fun save(name: String, b: Bitmap) = File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dir.listFiles()?.forEach { it.delete() }   // old Taglish samples
        save("square_streak_30", ShareCards.render(ShareCards.streak(30, "Pearl"), Format.SQUARE))
        save("story_level_up", ShareCards.render(ShareCards.levelUp(6, Game.titleFor(6), "Pearl"), Format.STORY))
        save("story_passed", ShareCards.render(ShareCards.passedExam("Professional", "Pearl"), Format.STORY))
        save("square_badge_no_name", ShareCards.render(ShareCards.badge(Game.badge("master_num")!!, ""), Format.SQUARE))
        assertTrue(File(dir, "story_level_up.png").length() > 10_000)
    }
}
