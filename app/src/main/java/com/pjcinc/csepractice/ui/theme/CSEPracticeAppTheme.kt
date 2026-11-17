package com.pjcinc.csepractice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Navy,
    secondary = SunDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF0C2),
    onSecondaryContainer = Color(0xFF3D2E00),
    tertiary = Coral,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF151A25),
    surface = Color.White,
    onSurface = Color(0xFF151A25),
    surfaceVariant = Color(0xFFEDF0F7),
    onSurfaceVariant = Color(0xFF4A5263),
    outline = Color(0xFFC5CBD8),
    outlineVariant = Color(0xFFE1E5EE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFD),
    surfaceContainer = Color(0xFFF1F3F9),
    surfaceContainerHigh = Color(0xFFEBEEF5),
    surfaceContainerHighest = Color(0xFFE4E8F1)
)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Navy,
    primaryContainer = Color(0xFF243E80),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Sun,
    onSecondary = Color(0xFF3D2E00),
    secondaryContainer = Color(0xFF5A4500),
    onSecondaryContainer = Color(0xFFFFF0C2),
    tertiary = Color(0xFFFF8A8D),
    background = Color(0xFF0E1320),
    onBackground = Color(0xFFE4E8F2),
    surface = Color(0xFF161C2B),
    onSurface = Color(0xFFE4E8F2),
    surfaceVariant = Color(0xFF222A3C),
    onSurfaceVariant = Color(0xFFB7BFD0),
    outline = Color(0xFF475069),
    outlineVariant = Color(0xFF2C3447),
    surfaceContainerLowest = Color(0xFF0B0F1A),
    surfaceContainerLow = Color(0xFF131927),
    surfaceContainer = Color(0xFF182033),
    surfaceContainerHigh = Color(0xFF1E263A),
    surfaceContainerHighest = Color(0xFF252E44)
)

/** Colours Material doesn't have a slot for (right / wrong feedback). */
@Immutable
data class Feedback(val good: Color, val goodBg: Color, val bad: Color, val badBg: Color)

val LocalFeedback = staticCompositionLocalOf { Feedback(Success, SuccessContainer, Danger, DangerContainer) }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun CSEPracticeAppTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val feedback = if (dark) Feedback(SuccessDark, SuccessContainerDark, DangerDark, DangerContainerDark)
    else Feedback(Success, SuccessContainer, Danger, DangerContainer)
    CompositionLocalProvider(LocalFeedback provides feedback) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
