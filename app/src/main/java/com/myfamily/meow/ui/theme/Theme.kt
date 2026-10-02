package com.myfamily.meow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** Always light, fixed Figma palette (no dynamic color). */
private val ColorScheme = lightColorScheme(
    primary = Pink,
    onPrimary = OnMint,
    primaryContainer = PinkLight,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    secondaryContainer = PinkSoft,
    onSecondaryContainer = TextPrimary,
    error = SwipeExclude,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Surface,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface,
    surfaceContainerHighest = SurfaceHigh,
    outline = Outline,
    outlineVariant = Outline,
)

@Composable
fun MEOWTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = Typography) {
        ProvideDesignScale(content)
    }
}
