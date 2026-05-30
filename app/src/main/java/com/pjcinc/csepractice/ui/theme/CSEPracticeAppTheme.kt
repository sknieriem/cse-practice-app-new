package com.pjcinc.csepractice.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val BlueLightScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Blue80
)

private val BlueDarkScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Blue40
)

private val GreenLightScheme = lightColorScheme(
    primary = Green40,
    secondary = GreenGrey40,
    tertiary = Green80
)

private val GreenDarkScheme = darkColorScheme(
    primary = Green80,
    secondary = GreenGrey80,
    tertiary = Green40
)

/**
 * App theme. Reads dark-mode and color-scheme preferences from DataStore so changes
 * in [SettingsActivity] propagate immediately wherever this theme is used.
 *
 * @param dynamicColor when true and on Android 12+, falls back to Material You dynamic colors
 *   unless the user has explicitly chosen a named color scheme (Blue/Green).
 */
@Composable
fun CSEPracticeAppTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme by PreferencesDataStore
        .darkModeFlow(context)
        .collectAsState(initial = isSystemInDarkTheme())
    val scheme by PreferencesDataStore
        .colorSchemeFlow(context)
        .collectAsState(initial = "Default")

    val isDefaultScheme = scheme == "Default"
    val colorScheme = when {
        // Only use dynamic colors when the user hasn't explicitly chosen a named scheme.
        isDefaultScheme && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        scheme == "Blue" -> if (darkTheme) BlueDarkScheme else BlueLightScheme
        scheme == "Green" -> if (darkTheme) GreenDarkScheme else GreenLightScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
