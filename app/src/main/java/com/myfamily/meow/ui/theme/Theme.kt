package com.myfamily.meow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** Always dark, fixed palette (no dynamic color) to match the mockups. */
private val ColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = OnMint,
    primaryContainer = MintDark,
    onPrimaryContainer = Mint,
    secondary = TextSecondary,
    error = Coral,
    background = Background,
    onBackground = TextPrimary,
    surface = Background,
    onSurface = TextPrimary,
    surfaceVariant = Surface,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHigh,
    outline = Outline,
    outlineVariant = Outline,
)

@Composable
fun MEOWTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        content = content,
    )
}
