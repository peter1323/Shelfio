package com.shelfio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ShelfioColors = darkColorScheme(
    primary = ShelfioBlue,
    onPrimary = TextPrimary,
    secondary = ShelfioCyan,
    onSecondary = Background,
    tertiary = ShelfioViolet,
    onTertiary = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    outline = TextSecondary,
    error = Color(0xFFFF6B7A),
)

// Shelfio solo tiene tema oscuro.
@Composable
fun ShelfioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShelfioColors,
        typography = ShelfioTypography,
        content = content,
    )
}
