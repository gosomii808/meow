package com.myfamily.meow.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.myfamily.meow.ui.theme.AmountStyle
import com.myfamily.meow.ui.theme.TextPrimary

/** "Verify" title shown at the top of every mockup screen. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text("Verify", color = TextPrimary, fontSize = 22.sp, style = AmountStyle, modifier = modifier)
}
