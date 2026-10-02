package com.myfamily.meow.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.myfamily.meow.R
import com.myfamily.meow.classification.Category
import com.myfamily.meow.ui.theme.Orbit
import com.myfamily.meow.ui.theme.PinkLight
import com.myfamily.meow.ui.theme.Surface
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.color
import com.myfamily.meow.ui.theme.dz
import com.myfamily.meow.ui.theme.sz

@Composable
fun CatFace(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.cat_face), contentDescription = null, modifier = modifier.size(size))
}

/** "고양이지갑" wordmark in Orbit, sizes in Figma px. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, fontPx: Int = 35) {
    Text("고양이지갑", fontFamily = Orbit, fontSize = fontPx.sz, color = TextPrimary, modifier = modifier)
}

/** Cat + wordmark as on the history/report top-left (cat 53, text 25) or signup (cat 77, text 35). */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, catPx: Int = 53, fontPx: Int = 25, onClick: (() -> Unit)? = null) {
    Row(
        modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatFace(catPx.dz)
        Wordmark(fontPx = fontPx)
    }
}

/** Full-width rounded action button (tutorial "다음": 383×74, radius 15, 25px semibold). */
@Composable
fun DesignButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = PinkLight,
    content: Color = TextPrimary,
    heightPx: Int = 74,
    fontPx: Int = 25,
    radiusPx: Int = 15,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(heightPx.dz)
            .clip(RoundedCornerShape(radiusPx.dz))
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, fontSize = fontPx.sz, fontWeight = FontWeight.SemiBold)
    }
}

/** Pill chip (history: 89×39, radius 20, 22px white). */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, fontPx: Int = 22, textColor: Color = Color.White) {
    Text(
        text,
        color = textColor,
        fontSize = fontPx.sz,
        modifier = modifier
            .clip(RoundedCornerShape(20.dz))
            .background(color)
            .padding(horizontal = 16.dz, vertical = 5.dz),
    )
}

@Composable
fun CategoryPill(category: Category, modifier: Modifier = Modifier, fontPx: Int = 22) =
    Pill(category.label, category.color, modifier, fontPx)

/** White card with the soft drop shadow used across the Figma frames. */
fun Modifier.designCard(shape: Shape, elevation: Dp, color: Color = Surface): Modifier =
    shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
        .clip(shape)
        .background(color)
