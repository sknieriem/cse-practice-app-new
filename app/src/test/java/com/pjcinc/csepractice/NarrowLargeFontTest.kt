package com.pjcinc.csepractice

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Small phone (360 dp wide) with big system text. Walks every screen with choice rows and fails if any
 * text breaks in the middle of a word (e.g. "Subprof / essiona / l"). Screenshots go to roborazzi/40_*.
 */
abstract class NarrowLargeFontBase(private val fontScale: Float, private val tag: String) {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) = RuntimeEnvironment.setFontScale(fontScale)
    }).around(compose)

    private val problems = mutableListOf<String>()

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/${tag}_$name.png")
    }

    private fun waitText(t: String) = compose.waitUntil(30_000) {
        compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** Every visible text whose line break falls between two letters = a word split in half. */
    private fun checkNoMidWordBreaks(screen: String) {
        compose.waitForIdle()
        val nodes = compose.onAllNodes(hasAnyText(), useUnmergedTree = true).fetchSemanticsNodes()
        for (n in nodes) {
            val action = n.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: continue
            val results = mutableListOf<TextLayoutResult>()
            action.action?.invoke(results)
            val layout = results.firstOrNull() ?: continue
            val text = layout.layoutInput.text.text
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line, visibleEnd = false)
                if (end <= 0 || end >= text.length) continue
                val before = text[end - 1]; val after = text[end]
                if (before.isLetterOrDigit() && after.isLetterOrDigit()) {
                    problems += "$screen: \"${text.take(60)}\" breaks at \"${text.substring(maxOf(0, end - 6), end)}|${text.substring(end, minOf(text.length, end + 6))}\""
                }
            }
        }
    }

    private fun hasAnyText() = androidx.compose.ui.test.SemanticsMatcher("has text") {
        it.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
    }

    private fun nav(label: String) = compose.onAllNodesWithText(label).last().performClick()

    @Test
    fun every_choice_row_fits_without_breaking_words() {
        waitText("Tara, simulan na!")
        shot("01_onboarding_top"); checkNoMidWordBreaks("onboarding")
        compose.onNodeWithText("Daily goal", substring = true).performScrollTo()
        shot("02_onboarding_goal"); checkNoMidWordBreaks("onboarding (goal)")
        compose.onNodeWithText("Tara, simulan na!", substring = true).performScrollTo().performClick()

        waitText("Practice now")
        shot("03_home"); checkNoMidWordBreaks("home")

        nav("Practice"); waitText("Subjects")
        shot("04_practice"); checkNoMidWordBreaks("practice")
        compose.onNodeWithText("How many", substring = true).performScrollTo()
        shot("05_practice_custom"); checkNoMidWordBreaks("practice (custom)")

        nav("Progress"); waitText("Your readiness")
        shot("06_progress"); checkNoMidWordBreaks("progress")

        nav("Settings"); waitText("Exam date")
        shot("07_settings"); checkNoMidWordBreaks("settings")
        compose.onNodeWithText("Theme", substring = true).performScrollTo()
        shot("08_settings_theme"); checkNoMidWordBreaks("settings (theme)")

        nav("Home"); waitText("Practice now")
        compose.onNodeWithText("Practice now").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("option_0")).fetchSemanticsNodes().isNotEmpty() }
        checkNoMidWordBreaks("quiz")
        compose.onNodeWithTag("option_1").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("feedback")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("feedback").performScrollTo()
        shot("09_quiz_feedback"); checkNoMidWordBreaks("quiz feedback")

        assertTrue("Words broken mid-word at font ${fontScale}x:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())
    }
}

private fun SemanticsNodeInteractionCollection.last(): SemanticsNodeInteraction = this[fetchSemanticsNodes().size - 1]

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class NarrowFont130Test : NarrowLargeFontBase(1.3f, "40_font130")

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class NarrowFont200Test : NarrowLargeFontBase(2.0f, "41_font200")
