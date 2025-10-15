package com.example.csepractice.ui.theme

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
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

@Composable
fun CSEPracticeAppTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkThemeState = PreferencesDataStore.darkModeFlow(context).collectAsState(initial = isSystemInDarkTheme())
    val schemeState = PreferencesDataStore.colorSchemeFlow(context).collectAsState(initial = "Default")

    val darkTheme = darkThemeState.value
    val scheme = schemeState.value

    // Log to debug
    Log.d("ThemeDebug", "Current scheme: $scheme, darkTheme: $darkTheme")

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}