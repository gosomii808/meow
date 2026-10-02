package com.myfamily.meow.ui.review

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ClassificationSource
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.ui.common.Pill
import com.myfamily.meow.ui.common.designCard
import com.myfamily.meow.ui.common.formatTime
import com.myfamily.meow.ui.common.isIncome
import com.myfamily.meow.ui.common.signedWon
import com.myfamily.meow.ui.theme.Amber
import com.myfamily.meow.ui.theme.AmberDark
import com.myfamily.meow.ui.theme.ChipGray
import com.myfamily.meow.ui.theme.Sky
import com.myfamily.meow.ui.theme.SwipeExclude
import com.myfamily.meow.ui.theme.SwipeInclude
import com.myfamily.meow.ui.theme.SwipeSettle
import com.myfamily.meow.ui.theme.TextMuted
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary
import com.myfamily.meow.ui.theme.dz
import com.myfamily.meow.ui.theme.sz
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Color for each swipe direction (Figma 내역 애니메이션 frames and bottom bar). */
val SwipeAction.color: Color
    get() = when (this) {
        SwipeAction.INCLUDE -> SwipeInclude
        SwipeAction.EXCLUDE -> SwipeExclude
        SwipeAction.SETTLE -> SwipeSettle
    }

val SwipeAction.icon: ImageVector
    get() = when (this) {
        SwipeAction.INCLUDE -> Icons.AutoMirrored.Filled.ArrowForward
        SwipeAction.EXCLUDE -> Icons.AutoMirrored.Filled.ArrowBack
        SwipeAction.SETTLE -> Icons.AutoMirrored.Filled.ArrowBack
    }

/** Figma uses one arrow glyph ("arrow-left-02") rotated per direction; 정산 is the left arrow turned up. */
val SwipeAction.iconRotation: Float
    get() = if (this == SwipeAction.SETTLE) 90f else 0f

/**
 * Drag right past 30% of the width to include, left to exclude, up to settle (split the bill).
 * Tap to edit. [command] lets the bottom buttons fling the card the same way; a settle that
 * gets cancelled comes back when [resetKey] changes.
 */
@Composable
fun SwipeCard(
    transaction: ExpenseTransaction,
    aiRunning: Boolean,
    command: SwipeAction?,
    resetKey: Int,
    onSwiped: (SwipeAction) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val thresholdX = width * 0.3f
        val thresholdY = height * 0.22f
        val offsetX = remember(transaction.id, resetKey) { Animatable(0f) }
        val offsetY = remember(transaction.id, resetKey) { Animatable(0f) }
        val scope = rememberCoroutineScope()

        suspend fun fling(action: SwipeAction) {
            when (action) {
                SwipeAction.INCLUDE -> offsetX.animateTo(width * 1.5f, tween(220))
                SwipeAction.EXCLUDE -> offsetX.animateTo(-width * 1.5f, tween(220))
                SwipeAction.SETTLE -> offsetY.animateTo(-height * 1.4f, tween(220))
            }
            onSwiped(action)
        }

        LaunchedEffect(command) { if (command != null) fling(command) }

        val active: SwipeAction? = when {
            offsetY.value < 0 && -offsetY.value > abs(offsetX.value) -> SwipeAction.SETTLE
            offsetX.value > 0 -> SwipeAction.INCLUDE
            offsetX.value < 0 -> SwipeAction.EXCLUDE
            else -> null
        }
        val strength = when (active) {
            SwipeAction.SETTLE -> -offsetY.value / thresholdY
            null -> 0f
            else -> abs(offsetX.value) / thresholdX
        }.coerceIn(0f, 1f)

        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .graphicsLayer { rotationZ = offsetX.value / width * 10f }
                .pointerInput(transaction.id, resetKey) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val x = offsetX.value
                                val y = offsetY.value
                                when {
                                    -y > thresholdY && -y > abs(x) -> fling(SwipeAction.SETTLE)
                                    x > thresholdX -> fling(SwipeAction.INCLUDE)
                                    x < -thresholdX -> fling(SwipeAction.EXCLUDE)
                                    else -> {
                                        launch { offsetX.animateTo(0f) }
                                        offsetY.animateTo(0f)
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                            scope.launch { offsetY.animateTo(0f) }
                        },
                    ) { change, drag ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + drag.x)
                            // Only upward movement means something (정산).
                            offsetY.snapTo((offsetY.value + drag.y).coerceAtMost(0f))
                        }
                    }
                }
                .clickable(onClick = onClick),
        ) {
            TransactionCardFace(transaction, active, strength, aiRunning)
        }
    }
}

/** Figma card: 321×370, radius 10, chip 123×39, merchant 30, amount 50, time/card 23. */
@Composable
fun TransactionCardFace(
    transaction: ExpenseTransaction,
    active: SwipeAction? = null,
    strength: Float = 0f,
    aiRunning: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dz)
    Box(Modifier.fillMaxSize().designCard(shape, 6.dz)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dz, vertical = 22.dz),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Pill(
                if (transaction.isIncome) "수입" else transaction.category.label,
                if (transaction.isIncome) Sky else ChipGray,
                fontPx = 23,
            )
            Spacer(Modifier.height(16.dz))
            Text(
                transaction.merchant,
                color = TextPrimary,
                fontSize = 30.sz,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                classificationCaption(transaction, aiRunning),
                color = TextMuted,
                fontSize = 14.sz,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            val amount = transaction.signedWon()
            Text(
                amount,
                color = if (transaction.isIncome) Sky else TextPrimary,
                // Figma's 50px fits "58,200원"; longer amounts shrink so they stay on one line.
                fontSize = (50 * minOf(1f, 8f / amount.length)).sz,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Badges(transaction)
            Spacer(Modifier.weight(1f))
            Text(formatTime(transaction.transactionTime), color = TextSecondary, fontSize = 21.sz, maxLines = 1)
            Text(
                transaction.sourceLabel,
                color = TextSecondary,
                fontSize = 21.sz,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (active != null && strength > 0.02f) {
            // Tint + border as in the 애니메이션 frames (color at 20%, 3px stroke).
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(strength)
                    .clip(shape)
                    .background(active.color.copy(alpha = 0.2f))
                    .border(3.dz, active.color, shape),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(83.dz)
                    .alpha(strength)
                    .clip(CircleShape)
                    .background(active.color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(active.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dz).rotate(active.iconRotation))
            }
        }
    }
}

private fun classificationCaption(tx: ExpenseTransaction, aiRunning: Boolean): String = when {
    tx.isIncome && tx.transferLikely -> "내 다른 계좌에서 옮긴 돈 같아요"
    tx.isIncome -> "들어온 돈이에요"
    tx.finalCategory != null -> "직접 고른 카테고리예요"
    aiRunning -> "AI가 분류하는 중…"
    tx.transferLikely && tx.category == Category.ETC -> "계좌 이동이면 왼쪽으로 밀어주세요"
    tx.classificationSource == ClassificationSource.AI -> "AI가 ${tx.category.label}(으)로 분류했어요"
    tx.classificationSource == ClassificationSource.APP -> tx.memo ?: "사용한 앱으로 분류했어요"
    tx.classificationSource == ClassificationSource.USER -> "지난번 수정 기록으로 분류했어요"
    tx.category != Category.ETC -> "가맹점 규칙으로 분류했어요"
    else -> "탭해서 카테고리를 정해주세요"
}

/** Spec §9 hints, side by side so they never push the card's bottom lines out. */
@Composable
private fun Badges(tx: ExpenseTransaction) {
    if (!tx.transferLikely && tx.duplicateGroupId == null) return
    Row(Modifier.padding(top = 6.dz), horizontalArrangement = Arrangement.spacedBy(6.dz)) {
        if (tx.transferLikely) Badge("계좌 이동 추정")
        if (tx.duplicateGroupId != null) Badge("중복 가능성")
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        "⚠ $text",
        color = Amber,
        fontSize = 13.sz,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dz))
            .background(AmberDark)
            .padding(horizontal = 8.dz, vertical = 3.dz),
    )
}
