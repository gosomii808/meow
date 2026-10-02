package com.myfamily.meow.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Figma frames are 450 wide; sizes in the design are scaled to the phone's width. */
private const val DESIGN_WIDTH = 450f

val LocalDesignScale = staticCompositionLocalOf { 1f }

@Composable
fun ProvideDesignScale(content: @Composable () -> Unit) {
    val scale = LocalConfiguration.current.screenWidthDp / DESIGN_WIDTH
    CompositionLocalProvider(LocalDesignScale provides scale, content = content)
}

/** Figma px → dp. */
val Number.dz: Dp
    @Composable @ReadOnlyComposable
    get() = (toFloat() * LocalDesignScale.current).dp

/** Figma px → text size that keeps the design's proportions regardless of system font scale. */
val Number.sz: TextUnit
    @Composable @ReadOnlyComposable
    get() = with(LocalDensity.current) { (toFloat() * LocalDesignScale.current).dp.toSp() }
