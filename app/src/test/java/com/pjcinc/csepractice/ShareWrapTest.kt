package com.pjcinc.csepractice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.pjcinc.csepractice.ui.share.ShareCards
import com.pjcinc.csepractice.ui.share.ShareLabels
import com.pjcinc.csepractice.ui.share.SharePreview
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
 * Same standard as NarrowLargeFontTest: 360 dp phone, big system font, fail on any line break between
 * two letters. Covers the Square / Story preview and every share-moment button label, both languages.
 */
abstract class ShareWrapBase(private val fontScale: Float, private val tag: String) {
    private val compose = createComposeRule()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) = RuntimeEnvironment.setFontScale(fontScale)
    }).around(compose)

    private fun hasAnyText() = SemanticsMatcher("has text") {
        it.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
    }

    @Test
    fun share_preview_and_buttons_never_break_words() {
        val problems = mutableListOf<String>()
        val shown = androidx.compose.runtime.mutableStateOf("en" to ShareCards.Format.SQUARE)
        compose.setContent {
            val (lang, format) = shown.value
                MaterialTheme {
                    Surface {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            SharePreview(ShareCards.streak(30, "Pearl"), format, {}, lang, Modifier.fillMaxWidth())
                            ShareLabels.all(lang).forEach {
                                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text(it, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
        }
        for (lang in listOf("en", "tl")) for (format in ShareCards.Format.entries) {
            shown.value = lang to format
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${tag}_share_${lang}_${format.name.lowercase()}.png")
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
                    if (text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit())
                        problems += "$lang/$format: \"$text\" breaks at ${text.substring(maxOf(0, end - 6), end)}|${text.substring(end)}"
                }
            }
        }
        assertTrue("Words broken mid-word at font ${fontScale}x:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
    onNode(androidx.compose.ui.test.isRoot())

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class ShareWrap130Test : ShareWrapBase(1.3f, "42_font130")

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class ShareWrap200Test : ShareWrapBase(2.0f, "43_font200")
