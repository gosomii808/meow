package com.myfamily.meow.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import com.myfamily.meow.classification.Category

/** Simple single-color glyph per category, drawn on a 24×24 grid. */
@Composable
fun CategoryIcon(category: Category, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) { drawGlyph(category, tint) }
    }
}

private fun DrawScope.drawGlyph(category: Category, c: Color) {
    val s = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, Offset(x1, y1), Offset(x2, y2), 2f, StrokeCap.Round)
    fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), c, style = s)
    fun dot(x: Float, y: Float, r: Float = 1.6f) = drawCircle(c, r, Offset(x, y))

    when (category) {
        Category.FOOD -> { // rice bowl + chopsticks
            drawArc(c, 0f, 180f, false, Offset(4f, 2f), Size(16f, 16f), style = s)
            line(3f, 10f, 21f, 10f)
            line(14f, 2f, 20f, 7f)
            line(16f, 2f, 22f, 7f)
        }
        Category.CAFE -> { // coffee cup
            path { moveTo(6f, 9f); lineTo(16f, 9f); lineTo(15f, 18f); lineTo(7f, 18f); close() }
            drawArc(c, -90f, 180f, false, Offset(15f, 9f), Size(6f, 7f), style = s)
            line(5f, 21f, 17f, 21f)
            line(9f, 3f, 9f, 6f); line(13f, 3f, 13f, 6f)
        }
        Category.TRANSPORT -> { // bus
            drawRoundRect(c, Offset(4f, 5f), Size(16f, 13f), CornerRadius(3f), style = s)
            line(4f, 10f, 20f, 10f)
            dot(8f, 20f, 1.6f); dot(16f, 20f, 1.6f)
        }
        Category.SHOPPING -> { // shopping bag
            drawRoundRect(c, Offset(6f, 8f), Size(12f, 12f), CornerRadius(1.5f), style = s)
            drawArc(c, 180f, 180f, false, Offset(9f, 4f), Size(6f, 8f), style = s)
        }
        Category.LIVING -> { // house
            path { moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f) }
            path { moveTo(6f, 11f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 11f) }
            path { moveTo(10f, 20f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 20f) }
        }
        Category.CULTURE -> drawPath(star(12f, 12f, 9f, 4f), c) // star
        Category.EDUCATION -> { // book
            path { moveTo(6f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(6f, 20f); close() }
            line(9f, 4f, 9f, 20f)
            line(12f, 9f, 16f, 9f); line(12f, 13f, 16f, 13f)
        }
        Category.MEDICAL -> { line(12f, 6f, 12f, 18f); line(6f, 12f, 18f, 12f) } // plus
        Category.TRAVEL -> { // paper plane
            path { moveTo(3f, 12f); lineTo(21f, 4f); lineTo(13f, 21f); lineTo(11f, 13f); close() }
            line(11f, 13f, 21f, 4f)
        }
        Category.SUBSCRIPTION -> { // renew arrow
            drawArc(c, -50f, 280f, false, Offset(5f, 5f), Size(14f, 14f), style = s)
            path { moveTo(16f, 3f); lineTo(19f, 6f); lineTo(15f, 7.5f) }
        }
        Category.ETC -> { dot(7f, 12f); dot(12f, 12f); dot(17f, 12f) }
    }
}

/** 5-point star path centered at (cx, cy). */
private fun star(cx: Float, cy: Float, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = Math.toRadians((-90 + i * 36).toDouble())
        val x = cx + (r * Math.cos(a)).toFloat()
        val y = cy + (r * Math.sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
