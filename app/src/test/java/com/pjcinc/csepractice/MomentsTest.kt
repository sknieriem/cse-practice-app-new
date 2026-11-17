package com.pjcinc.csepractice

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.pjcinc.csepractice.data.Prefs
import com.pjcinc.csepractice.data.UserState
import com.pjcinc.csepractice.ui.share.ShareCards
import com.pjcinc.csepractice.viewmodel.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** The emotional moments: exam eve/day, after the exam, coming back, the first "pasado" mock. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xhdpi")
class MomentsTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val prefs = Prefs(app)
    private val today = LocalDate.now().toEpochDay()

    private fun waitText(t: String, ms: Long = 30_000) =
        rule.waitUntil(ms) { rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        rule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun seed(examDay: Long, game: UserState) = runBlocking {
        prefs.setOnboarded(examDay, "Professional", 20)
        prefs.setFirstName("Ana")
        prefs.setInspiration("Para kina Mama at Papa", "")
        prefs.saveGame(game)
    }

    @Test
    fun moments() {
        // ---- exam day
        seed(today, UserState(streak = 12, bestStreak = 12, lastActiveDay = today - 1, xp = 2400))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Exam day!")
            shot("22_exam_day")
        }

        // ---- after the exam: ask, then celebrate a pass
        seed(today - 3, UserState(streak = 0, lastActiveDay = today - 3, xp = 2400))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("How did your exam go?")
            shot("23_post_exam")
            rule.onNodeWithText("I passed!", substring = true).performClick()
            waitText("PASADO!")
            shot("24_passed_exam")
        }

        // ---- coming back after 5 days: no guilt, progress saved, inspiration shown
        seed(-1, UserState(streak = 4, bestStreak = 9, lastActiveDay = today - 5, xp = 800))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Welcome back!")
            shot("25_comeback")
        }

        // ---- first mock at the passing rating: answer every item correctly
        seed(-1, UserState(lastActiveDay = today - 1, streak = 2, xp = 300))
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            waitText("Practice now")
            rule.onAllNodesWithText("Practice")[rule.onAllNodesWithText("Practice").fetchSemanticsNodes().size - 1].performClick()
            waitText("Mini mock")
            rule.onNodeWithText("Mini mock").performScrollTo().performClick()
            rule.waitUntil(30_000) { rule.onAllNodes(hasTestTag("option_0")).fetchSemanticsNodes().isNotEmpty() }
            lateinit var vm: AppViewModel
            sc.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
            val n = vm.quiz.value!!.size
            for (i in 0 until n) {
                val s = vm.quiz.value!!
                val q = vm.quizQuestions.value[s.index]
                val pos = s.order[s.index].indexOf(q.correctIndex())
                rule.onNodeWithTag("option_$pos").performScrollTo().performClick()
                rule.onNodeWithTag(if (i < n - 1) "next" else "submit").performClick()
            }
            waitText("PASSING LEVEL!")
            shot("26_pasado")
            rule.onNodeWithText("See my results").performScrollTo().performClick()
            waitText("pass mark")
            shot("27_result_after_pasado")
            assertTrue(rule.onAllNodesWithText("First mock exam done!").fetchSemanticsNodes().isNotEmpty())
        }

        // ---- the share cards themselves
        val out = File("build/outputs/roborazzi").apply { mkdirs() }
        listOf(
            "28_card_pasado" to ShareCards.pasadoMock(85, "Ana", "tl"),
            "29_card_passed" to ShareCards.passedExam("Professional", "Ana", "en"),
            "30_card_progress" to ShareCards.progress("Ana", 72, 9, 12, "Clerk", 640, "en")
        ).forEach { (name, card) ->
            File(out, "$name.png").outputStream().use { ShareCards.render(card, 720).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
