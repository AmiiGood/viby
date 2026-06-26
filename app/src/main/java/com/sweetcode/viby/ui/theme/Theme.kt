package com.sweetcode.viby.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VibyColorScheme = darkColorScheme(
    primary = VibyAccent,
    onPrimary = Color.White,
    secondary = VibyAccentDark,
    onSecondary = Color.White,
    tertiary = VibyFavorite,
    background = VibyBackground,
    onBackground = VibyOnSurface,
    surface = VibySurface,
    onSurface = VibyOnSurface,
    surfaceVariant = VibySurfaceVariant,
    onSurfaceVariant = VibyOnSurfaceMuted,
)

@Composable
fun VibyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VibyColorScheme,
        typography = Typography,
        content = content
    )
}
