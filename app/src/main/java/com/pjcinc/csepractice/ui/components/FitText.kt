package com.pjcinc.csepractice.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Text for tight spots (nav labels, tiles, grid cards). Normally identical to Text. When a word
 * would not fit — so Android would split it like "Numerica / l" — or the text needs more lines
 * than allowed, it shrinks the font a step at a time (down to [minScale]) until it fits.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minScale: Float = 0.5f
) {
    var scale by remember(text) { mutableFloatStateOf(1f) }
    var ready by remember(text) { mutableStateOf(false) }
    val base = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    Text(
        text,
        modifier.drawWithContent { if (ready) drawContent() },
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        style = style.copy(
            fontSize = base * scale,
            lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight
        ),
        onTextLayout = { r ->
            if ((r.didOverflowWidth || r.hasVisualOverflow || breaksMidWord(r)) && scale > minScale) scale *= 0.9f
            else ready = true
        }
    )
}

/** True when a line break falls between two letters/digits, i.e. inside a word. */
fun breaksMidWord(r: TextLayoutResult): Boolean {
    val s = r.layoutInput.text.text
    for (line in 0 until r.lineCount - 1) {
        val end = r.getLineEnd(line, visibleEnd = false)
        if (end in 1 until s.length && s[end - 1].isLetterOrDigit() && s[end].isLetterOrDigit()) return true
    }
    return false
}

/** Bottom-nav label: all four tabs use the same size, capped at 1.3x system text so none splits. */
@Composable
fun NavLabel(text: String) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val style = androidx.compose.material3.MaterialTheme.typography.labelMedium
    val base = if (style.fontSize.isSpecified) style.fontSize.value else 12f
    val size = (base * minOf(1f, 1.3f / fontScale)).sp
    Text(text, style = style.copy(fontSize = size, lineHeight = size * 1.2f), maxLines = 1, softWrap = false)
}
