package com.myfamily.meow.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import com.myfamily.meow.R
import com.myfamily.meow.ui.theme.dz
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

// Blink cycle: open → half → closed → half → open.
private val BLINK_FRAMES = listOf(R.drawable.cat_eye1, R.drawable.cat_eye2, R.drawable.cat_eye3, R.drawable.cat_eye2)
private val FRAME_MS = listOf(2600L, 70L, 110L, 70L) // how long each frame holds before the next

// Flailing cat played while being dragged (아둥바둥).
private val STRUGGLE_FRAMES = listOf(
    R.drawable.cat_struggle1, R.drawable.cat_struggle2, R.drawable.cat_struggle3, R.drawable.cat_struggle4,
    R.drawable.cat_struggle5, R.drawable.cat_struggle6, R.drawable.cat_struggle7, R.drawable.cat_struggle8,
    R.drawable.cat_struggle9, R.drawable.cat_struggle10,
)

/**
 * Home mascot: a wallet with a cat peeking over it. The cat blinks on its own, and can be
 * grabbed and dragged — it follows the finger and wriggles, then springs back onto the wallet.
 */
@Composable
fun AnimatedHomeCat(modifier: Modifier = Modifier, onDraggingChange: (Boolean) -> Unit = {}) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(FRAME_MS[frame])
            frame = (frame + 1) % BLINK_FRAMES.size
        }
    }

    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var dragging by remember { mutableStateOf(false) }

    // Cycle the flailing frames while held; a short tail keeps flailing as it springs back.
    var struggleFrame by remember { mutableIntStateOf(0) }
    var struggleActive by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        if (dragging) {
            struggleActive = true
            while (true) {
                delay(60)
                struggleFrame = (struggleFrame + 1) % STRUGGLE_FRAMES.size
            }
        } else if (struggleActive) {
            repeat(6) {
                delay(60)
                struggleFrame = (struggleFrame + 1) % STRUGGLE_FRAMES.size
            }
            struggleActive = false
        }
    }

    // Fast wobble used only while being dragged (the "struggling" motion).
    val t = rememberInfiniteTransition(label = "wriggle")
    val wobble by t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(85, easing = LinearEasing), RepeatMode.Reverse),
        label = "wobble",
    )
    // More struggle the further it's pulled from home.
    val pull = (hypot(offset.value.x, offset.value.y) / 120f).coerceIn(0f, 1f)
    val rotation = if (dragging) wobble * (6f + 10f * pull) else 0f

    Box(modifier) {
        // Wallet is clipped to this box so its bottom ends at the summary card's top edge
        // (the box bottom sits there), mimicking being tucked behind the card.
        Box(Modifier.matchParentSize().clipToBounds()) {
            Image(
                painterResource(R.drawable.cat_wallet_only),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 50.dz)
                    .fillMaxWidth(0.92f),
            )
        }
        Image(
            painterResource(if (struggleActive) STRUGGLE_FRAMES[struggleFrame] else BLINK_FRAMES[frame]),
            contentDescription = "고양이",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset(y = (-30).dz) // rest the cat's paws on the wallet's top
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .graphicsLayer {
                    rotationZ = rotation
                    transformOrigin = TransformOrigin(0.5f, 0.85f)
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true; onDraggingChange(true) },
                        onDrag = { change, delta ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + delta) }
                        },
                        onDragEnd = {
                            dragging = false; onDraggingChange(false)
                            scope.launch { offset.animateTo(Offset.Zero, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) }
                        },
                        onDragCancel = {
                            dragging = false; onDraggingChange(false)
                            scope.launch { offset.animateTo(Offset.Zero, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) }
                        },
                    )
                },
        )
    }
}
