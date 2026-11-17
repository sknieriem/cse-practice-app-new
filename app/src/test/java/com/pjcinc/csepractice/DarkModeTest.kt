package com.pjcinc.csepractice

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phone in dark mode ("Same as phone" theme): Home and a quiz answer. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-night-xhdpi")
class DarkModeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dark_home_and_quiz() {
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Tara, simulan na!", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Tara, simulan na!", substring = true).performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Practice now").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/20_dark_home.png")
        rule.onNodeWithText("Practice now").performClick()
        rule.waitUntil(30_000) { rule.onAllNodes(hasTestTag("option_0")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("option_1").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("feedback")).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/21_dark_quiz.png")
    }
}
