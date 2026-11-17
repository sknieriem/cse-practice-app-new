package com.pjcinc.csepractice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pjcinc.csepractice.ui.theme.AnaColor
import com.pjcinc.csepractice.ui.theme.GiColor
import com.pjcinc.csepractice.ui.theme.NumColor
import com.pjcinc.csepractice.ui.theme.VerbColor

data class SubjectStyle(val short: String, val color: Color, val icon: ImageVector)

fun subjectStyle(category: String): SubjectStyle = when (category) {
    "Numerical Ability" -> SubjectStyle("Numerical", NumColor, Icons.Filled.Calculate)
    "Verbal Ability" -> SubjectStyle("Verbal", VerbColor, Icons.AutoMirrored.Filled.MenuBook)
    "Analytical Ability" -> SubjectStyle("Analytical", AnaColor, Icons.Filled.Extension)
    else -> SubjectStyle("Gen Info", GiColor, Icons.Filled.AccountBalance)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Rounded bar: a plain Box so it looks the same on every Material version. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
fun IconBubble(icon: ImageVector, color: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

/** A tappable card row: icon, title/subtitle, trailing content. Whole row is the tap target. */
@Composable
fun ActionCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, content = trailing)
            }
        }
    }
}

@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

fun formatClock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
