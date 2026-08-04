package com.androidcraft.studio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = CraftViolet,
    onPrimary = CraftBackground,
    secondary = CraftCyan,
    background = CraftBackground,
    onBackground = CraftOnSurface,
    surface = CraftSurface,
    onSurface = CraftOnSurface,
    surfaceVariant = CraftSurfaceHigh,
    onSurfaceVariant = CraftOnSurfaceMuted,
    error = CraftError,
)

private val LightColors = lightColorScheme(
    primary = CraftLightPrimary,
    secondary = CraftVioletDark,
    background = CraftLightBackground,
    onBackground = CraftLightOnSurface,
    surface = CraftLightSurface,
    onSurface = CraftLightOnSurface,
    error = CraftError,
)

@Composable
fun AndroidCraftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CraftTypography,
        content = content,
    )
}
