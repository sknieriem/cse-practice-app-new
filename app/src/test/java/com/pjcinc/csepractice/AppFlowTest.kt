package com.pjcinc.csepractice

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app (Room, DataStore, the bundled question bank) through the main flows and
 * saves a screenshot of each screen to app/build/outputs/roborazzi/.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xhdpi")
class AppFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun waitText(text: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitTag(tag: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun text(t: String): SemanticsNodeInteraction = rule.onNodeWithText(t, substring = true)

    private fun answerAll(count: Int, tapTextFirst: Boolean = false) {
        for (i in 0 until count) {
            waitTag("option_0")
            if (tapTextFirst && i == 0) {
                // bug fix check: tapping the option TEXT (not just a radio dot) selects it
                rule.onNodeWithTag("option_1").performScrollTo().performClick()
            } else {
                rule.onNodeWithTag("option_${i % 4}").performScrollTo().performClick()
            }
            if (i < count - 1) rule.onNodeWithTag("next").performClick() else rule.onNodeWithTag("submit").performClick()
        }
    }

    @Test
    fun full_journey_with_screenshots() {
        // ---- first run: onboarding
        waitText("Tara, simulan na!")
        shot("01_onboarding")
        text("Tara, simulan na!").performClick()

        // ---- home
        waitText("Practice now")
        shot("02_home")

        // ---- smart practice: first question, then answer it and see feedback
        text("Practice now").performClick()
        waitTag("option_0")
        shot("03_quiz_question")
        rule.onNodeWithTag("option_2").performScrollTo().performClick()
        waitTag("feedback")
        rule.onNodeWithTag("feedback").performScrollTo()
        shot("04_quiz_feedback")

        // ---- back button shows the exit dialog; keep going
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitText("Take a break?")
        shot("05_exit_dialog")
        text("Keep going").performClick()

        // ---- finish the session (question 1 is already answered)
        rule.onNodeWithTag("next").performClick()
        answerAll(14)
        waitText("pass mark")
        shot("06_result")

        // ---- review shows your answer AND the correct answer
        val reviewMistakes = rule.onAllNodesWithText("Review my", substring = true).fetchSemanticsNodes().isNotEmpty()
        text(if (reviewMistakes) "Review my" else "See all answers").performScrollTo().performClick()
        waitText("Your answer")
        if (reviewMistakes) assertTrue(rule.onAllNodesWithText("Correct answer:", substring = true).fetchSemanticsNodes().isNotEmpty())
        shot("07_review")
        rule.onNodeWithContentDescription("Back to results").performClick()
        waitText("See all answers")
        text("Done").performScrollTo().performClick()

        // ---- home after one session: XP, streak, daily goal progress
        waitText("Practice now")
        shot("08_home_after_session")

        // ---- tabs
        rule.onAllNodesWithText("Practice").onFirstNavLabel().performClick()
        waitText("Subjects")
        shot("09_practice_hub")
        rule.onAllNodesWithText("Numerical", substring = true)[0].performClick()
        waitText("Practice all of Numerical")
        shot("10_subject_topics")
        rule.onNodeWithContentDescription("Back to subjects").performClick()

        rule.onAllNodesWithText("Progress").onFirstNavLabel().performClick()
        waitText("Your readiness")
        shot("11_progress")

        rule.onAllNodesWithText("Settings").onFirstNavLabel().performClick()
        waitText("Exam date")
        shot("12_settings")

        // ---- exam mode: timer counts down, no feedback; save & exit -> Continue card on Home
        rule.onAllNodesWithText("Practice").onFirstNavLabel().performClick()
        waitText("Mini mock")
        text("Mini mock").performScrollTo().performClick()
        waitTag("option_0")
        rule.onNodeWithTag("option_0").performClick()
        assertTrue(rule.onAllNodes(hasTestTag("feedback")).fetchSemanticsNodes().isEmpty())
        shot("13_exam_mode")
        rule.onNodeWithContentDescription("Exit session").performClick()
        waitText("Save & exit")
        text("Save & exit").performClick()
        rule.onAllNodesWithText("Home").onFirstNavLabel().performClick()
        waitText("Continue: Exam mode")
        shot("14_home_continue")

        // ---- resume restores the session
        text("Continue: Exam mode").performClick()
        waitTag("option_0")
        rule.onNodeWithContentDescription("All questions").performClick()
        waitText("1 answered")
        shot("15_resumed_grid")
    }
}

/** Bottom-nav labels are the last match on screen (titles may repeat the word). */
private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onFirstNavLabel(): SemanticsNodeInteraction {
    val n = fetchSemanticsNodes().size
    return this[n - 1]
}
