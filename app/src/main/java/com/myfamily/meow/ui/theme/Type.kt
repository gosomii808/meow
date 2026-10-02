package com.myfamily.meow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/** The mockups set the "Verify" wordmark and money amounts in a bold monospace face. */
val AmountStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
