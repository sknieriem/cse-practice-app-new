package com.pjcinc.csepractice.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pjcinc.csepractice.domain.Encourage
import com.pjcinc.csepractice.domain.Planner
import java.io.File

/**
 * Horizontal meter 0..100 with the 80 passing line marked. The fill turns green at the line.
 */
@Composable
fun ReadinessMeter(
    value: Int,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    fill: Color = MaterialTheme.colorScheme.primary,
    passFill: Color = Color(0xFF1E9E5A),
    markerColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(14.dp).align(Alignment.Center).clip(RoundedCornerShape(50)).background(track))
            Box(
                Modifier.fillMaxWidth((value / 100f).coerceIn(0f, 1f)).height(14.dp).align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(50)).background(if (value >= Planner.PASS_MARK) passFill else fill)
            )
            Box(
                Modifier.offset(x = w * (Planner.PASS_MARK / 100f) - 1.5.dp).width(3.dp).fillMaxHeight()
                    .background(markerColor)
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Text(
                "Pass ${Planner.PASS_MARK}",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.offset(x = maxWidth * (Planner.PASS_MARK / 100f) - 22.dp)
            )
        }
    }
}

/** Decodes the user's "who I'm doing this for" photo (stored as path?v=timestamp). */
@Composable
fun rememberInspirationBitmap(path: String) = remember(path) {
    val file = path.substringBefore("?v=")
    if (file.isBlank() || !File(file).exists()) null
    else runCatching {
        BitmapFactory.decodeFile(file, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
    }.getOrNull()
}

/** Shown on hard days: a low score, coming back after days away, exam day. */
@Composable
fun InspirationCard(line: String, photoPath: String, lang: String, modifier: Modifier = Modifier) {
    if (line.isBlank() && photoPath.isBlank()) return
    val bmp = rememberInspirationBitmap(photoPath)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (bmp != null) {
                Image(
                    bmp, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    Encourage.t(lang, "Remember why you started 💛", "Alalahanin kung para kanino ka lumalaban 💛"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (line.isNotBlank()) {
                    Text(
                        "“$line”",
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun CelebrationCard(c: Encourage.Celebration, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.emoji, fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(c.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(c.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
