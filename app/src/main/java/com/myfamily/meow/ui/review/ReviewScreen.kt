package com.myfamily.meow.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import com.myfamily.meow.aiCategorizer
import com.myfamily.meow.repository
import com.myfamily.meow.ui.common.TransactionEditDialog
import com.myfamily.meow.ui.common.formatWon
import com.myfamily.meow.ui.theme.AmountStyle
import com.myfamily.meow.ui.theme.Mint
import com.myfamily.meow.ui.theme.MintDark
import com.myfamily.meow.ui.theme.OnMint
import com.myfamily.meow.ui.theme.Outline
import com.myfamily.meow.ui.theme.Surface
import com.myfamily.meow.ui.theme.SurfaceHigh
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun ReviewScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: ReviewViewModel = viewModel(factory = viewModelFactory {
        initializer { ReviewViewModel(context.repository, context.aiCategorizer) }
    })
    val pending by vm.pending.collectAsStateWithLifecycle()
    val reviewedToday by vm.reviewedToday.collectAsStateWithLifecycle()
    val summaryDismissed by vm.summaryDismissed.collectAsStateWithLifecycle()
    val lastSwiped by vm.lastSwiped.collectAsStateWithLifecycle()
    val aiRunningIds by vm.aiRunningIds.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<ExpenseTransaction?>(null) }
    var adding by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        val list = pending
        when {
            list == null -> Unit
            list.isNotEmpty() -> CardStack(
                pending = list,
                aiRunningIds = aiRunningIds,
                onSwiped = vm::swipe,
                onClick = { editing = it },
            )
            reviewedToday.isNotEmpty() && !summaryDismissed -> ReviewSummary(
                reviewed = reviewedToday,
                onAdd = { adding = true },
                onFinish = vm::finish,
            )
            else -> EmptyReview(onAdd = { adding = true })
        }

        lastSwiped?.let { swiped ->
            LaunchedEffect(swiped) {
                delay(4_000)
                vm.clearUndo()
            }
            UndoBar(
                included = swiped.include,
                merchant = swiped.transaction.merchant,
                onUndo = vm::undo,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }

    editing?.let { tx ->
        TransactionEditDialog(
            initial = tx,
            date = LocalDate.now(),
            onDismiss = { editing = null },
            onSave = {
                vm.saveEdit(tx, it)
                editing = null
            },
        )
    }
    if (adding) {
        TransactionEditDialog(
            initial = null,
            date = LocalDate.now(),
            onDismiss = { adding = false },
            onSave = {
                vm.addManual(it)
                adding = false
            },
        )
    }
}

@Composable
private fun CardStack(
    pending: List<ExpenseTransaction>,
    aiRunningIds: Set<Long>,
    onSwiped: (ExpenseTransaction, Boolean) -> Unit,
    onClick: (ExpenseTransaction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("남은 ${pending.size}건", color = TextSecondary, fontSize = 18.sp)
        Spacer(Modifier.height(32.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .height(440.dp),
        ) {
            if (pending.size > 1) {
                // Next card peeking from behind.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .offset(y = 14.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Surface.copy(alpha = 0.5f))
                        .border(1.dp, Outline, RoundedCornerShape(24.dp)),
                )
            }
            val top = pending.first()
            SwipeCard(
                transaction = top,
                aiRunning = top.id in aiRunningIds,
                onSwiped = { include -> onSwiped(top, include) },
                onClick = { onClick(top) },
            )
        }

        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("← 제외", color = TextSecondary)
            Text("탭해서 수정", color = TextSecondary.copy(alpha = 0.6f), fontSize = 13.sp)
            Text("포함 →", color = TextSecondary)
        }
    }
}

@Composable
private fun EmptyReview(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "오늘의 소비를\n승인할 시간입니다",
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text("이 지출을 장부에 포함할까요?", color = TextSecondary, fontSize = 17.sp)
        Spacer(Modifier.height(40.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Mint)
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = OnMint)
            Spacer(Modifier.width(12.dp))
            Text("새로운 내역이 없어요", color = OnMint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAdd) { Text("+ 직접 기록하기", color = TextSecondary) }
    }
}

@Composable
private fun ReviewSummary(
    reviewed: List<ExpenseTransaction>,
    onAdd: () -> Unit,
    onFinish: () -> Unit,
) {
    val included = reviewed.filter { it.status == TransactionStatus.INCLUDED }
    val excludedCount = reviewed.size - included.size

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MintDark),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("오늘의 검토 완료", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(formatWon(included.sumOf { it.amount }), color = Mint, fontSize = 34.sp, style = AmountStyle)
        Spacer(Modifier.height(8.dp))
        Text("${included.size}건 승인 · ${excludedCount}건 제외", color = TextSecondary)
        Spacer(Modifier.height(28.dp))

        included.forEach { tx ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface)
                    .border(1.dp, Outline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tx.merchant, color = TextPrimary, fontSize = 17.sp)
                    Text(tx.sourceLabel, color = TextSecondary, fontSize = 14.sp)
                }
                Text(formatWon(tx.amount), color = TextPrimary, style = AmountStyle)
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onAdd,
            border = BorderStroke(1.dp, Outline),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = TextSecondary)
            Spacer(Modifier.width(8.dp))
            Text("직접 기록하기", color = TextSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onFinish,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = OnMint),
        ) {
            Text("저장하고 마치기", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun UndoBar(included: Boolean, merchant: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceHigh)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$merchant ${if (included) "포함했어요" else "제외했어요"}",
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUndo) { Text("실행 취소", color = Mint, fontWeight = FontWeight.Bold) }
    }
}
