package com.pjcinc.csepractice.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pjcinc.csepractice.domain.Encourage

/** Button labels for the share moments (one place, so the wrap test can check every one). */
object ShareLabels {
    fun square(lang: String) = Encourage.t(lang, "Square", "Square")
    fun story(lang: String) = Encourage.t(lang, "Story", "Story")
    fun shareNow(lang: String) = Encourage.t(lang, "Share 📤", "I-share 📤")
    fun notNow(lang: String) = Encourage.t(lang, "Not now", "Mamaya na")
    fun formatHint(lang: String, f: ShareCards.Format) = when (f) {
        ShareCards.Format.SQUARE -> Encourage.t(lang, "For a Facebook or Messenger post", "Para sa Facebook o Messenger post")
        ShareCards.Format.STORY -> Encourage.t(lang, "For TikTok and FB / IG Stories", "Para sa TikTok at FB / IG Stories")
    }
    fun levelUp(lang: String) = Encourage.t(lang, "Share my level up 📤", "I-share ang level up ko 📤")
    fun badge(lang: String) = Encourage.t(lang, "Share this badge 📤", "I-share ang badge 📤")
    fun streak(lang: String, days: Int) = Encourage.t(lang, "Share my $days-day streak 📤", "I-share ang $days-day streak ko 📤")
    fun perfect(lang: String) = Encourage.t(lang, "Share my perfect score 📤", "I-share ang perfect score ko 📤")
    fun firstMock(lang: String) = Encourage.t(lang, "Share this milestone 📤", "I-share ang milestone na 'to 📤")
    fun all(lang: String) = listOf(
        square(lang), story(lang), shareNow(lang), notNow(lang),
        formatHint(lang, ShareCards.Format.SQUARE), formatHint(lang, ShareCards.Format.STORY),
        levelUp(lang), badge(lang), perfect(lang), firstMock(lang)
    ) + ShareCards.STREAK_MILESTONES.map { streak(lang, it) }
}

/** Card preview with a Square / Story toggle. */
@Composable
fun SharePreview(
    card: ShareCards.Card,
    format: ShareCards.Format,
    onFormat: (ShareCards.Format) -> Unit,
    lang: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = false
) {
    val preview = remember(card, format) { ShareCards.render(card, format, 540).asImageBitmap() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareCards.Format.entries.forEach { f ->
                val label = if (f == ShareCards.Format.SQUARE) ShareLabels.square(lang) else ShareLabels.story(lang)
                val mod = Modifier.weight(1f).heightIn(min = 44.dp).testTag("format_${f.name.lowercase()}")
                if (f == format) Button(onClick = { onFormat(f) }, modifier = mod) { Text(label, maxLines = 1) }
                else OutlinedButton(onClick = { onFormat(f) }, modifier = mod) {
                    Text(label, maxLines = 1, color = if (onDark) Color.White else Color.Unspecified)
                }
            }
        }
        Text(
            ShareLabels.formatHint(lang, format),
            style = MaterialTheme.typography.labelMedium,
            color = if (onDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        val size = if (format == ShareCards.Format.SQUARE) Modifier.width(240.dp).height(240.dp) else Modifier.width(180.dp).height(320.dp)
        Image(preview, contentDescription = "Card to share", modifier = size.clip(RoundedCornerShape(14.dp)))
    }
}

/** Shown for every Share button: pick Square or Story, then open the Android share sheet. */
@Composable
fun ShareDialog(card: ShareCards.Card, lang: String, onShare: (ShareCards.Format) -> Unit, onDismiss: () -> Unit) {
    var format by rememberSaveable { mutableStateOf(ShareCards.Format.SQUARE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SharePreview(card, format, { format = it }, lang, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onShare(format) }, modifier = Modifier.testTag("share_now")) { Text(ShareLabels.shareNow(lang)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(ShareLabels.notNow(lang)) } }
    )
}
