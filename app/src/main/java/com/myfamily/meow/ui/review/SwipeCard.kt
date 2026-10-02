package com.myfamily.meow.ui.review

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.ui.common.formatTime
import com.myfamily.meow.ui.common.formatWon
import com.myfamily.meow.ui.theme.AmountStyle
import com.myfamily.meow.ui.theme.Coral
import com.myfamily.meow.ui.theme.CoralDark
import com.myfamily.meow.ui.theme.Mint
import com.myfamily.meow.ui.theme.MintDark
import com.myfamily.meow.ui.theme.OnMint
import com.myfamily.meow.ui.theme.Outline
import com.myfamily.meow.ui.theme.Surface
import com.myfamily.meow.ui.theme.SurfaceHigh
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Drag right past 30% of the width to include, left to exclude. Tap to edit. */
@Composable
fun SwipeCard(
    transaction: ExpenseTransaction,
    onSwiped: (include: Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val threshold = width * 0.3f
        val offsetX = remember(transaction.id) { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val progress = (offsetX.value / threshold).coerceIn(-1f, 1f)

        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { rotationZ = offsetX.value / width * 10f }
                .pointerInput(transaction.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val x = offsetX.value
                                if (abs(x) > threshold) {
                                    offsetX.animateTo(if (x > 0) width * 1.5f else -width * 1.5f, tween(200))
                                    onSwiped(x > 0)
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                }
                .clickable(onClick = onClick),
        ) {
            TransactionCardFace(transaction, progress)
        }
    }
}

/** [progress] in -1..1: negative tints toward exclude (coral), positive toward include (mint). */
@Composable
fun TransactionCardFace(transaction: ExpenseTransaction, progress: Float) {
    val shape = RoundedCornerShape(24.dp)
    val (accent, accentBg) = if (progress >= 0) Mint to MintDark else Coral to CoralDark
    val strength = abs(progress)

    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(lerp(Surface, accentBg, strength))
            .border(if (strength > 0.05f) 2.dp else 1.dp, lerp(Outline, accent, strength), shape),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                transaction.category.label,
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                transaction.merchant,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(formatWon(transaction.amount), color = TextPrimary, fontSize = 40.sp, style = AmountStyle)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatTime(transaction.transactionTime), color = TextSecondary, fontSize = 16.sp)
                Text(transaction.sourceLabel, color = TextSecondary, fontSize = 16.sp)
            }
        }

        if (strength > 0.2f) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .alpha(strength)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (progress > 0) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (progress > 0) "포함" else "제외",
                    tint = if (progress > 0) OnMint else TextPrimary,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}
