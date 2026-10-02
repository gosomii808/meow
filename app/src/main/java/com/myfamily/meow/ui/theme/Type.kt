package com.myfamily.meow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.myfamily.meow.R

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/** Figma sets the "고양이지갑" wordmark in Orbit (OFL, bundled in res/font). */
val Orbit = FontFamily(Font(R.font.orbit_regular))

/** Money amounts. Figma uses Inter Semi Bold/Bold; the system sans is the closest bundled face. */
val AmountStyle = TextStyle(fontWeight = FontWeight.Bold)
