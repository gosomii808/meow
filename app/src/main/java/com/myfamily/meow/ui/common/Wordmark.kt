package com.myfamily.meow.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.myfamily.meow.ui.theme.AmountStyle
import com.myfamily.meow.ui.theme.TextPrimary

/** App name title at the top of every screen ("Verify" in the mockups). */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text("고양이지갑", color = TextPrimary, fontSize = 22.sp, style = AmountStyle, modifier = modifier)
}
