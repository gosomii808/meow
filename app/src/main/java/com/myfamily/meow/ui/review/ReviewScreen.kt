package com.myfamily.meow.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myfamily.meow.aiCategorizer
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import com.myfamily.meow.repository
import com.myfamily.meow.ui.common.CatFace
import com.myfamily.meow.ui.common.DesignButton
import com.myfamily.meow.ui.common.TransactionEditDialog
import com.myfamily.meow.ui.common.Wordmark
import com.myfamily.meow.ui.common.designCard
import com.myfamily.meow.ui.common.formatWon
import com.myfamily.meow.ui.common.isIncome
import com.myfamily.meow.ui.history.HistoryRow
import com.myfamily.meow.ui.theme.Background
import com.myfamily.meow.ui.theme.Pink
import com.myfamily.meow.ui.theme.PinkLight
import com.myfamily.meow.ui.theme.Sky
import com.myfamily.meow.ui.theme.SwipeSettle
import com.myfamily.meow.ui.theme.TextMuted
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary
import com.myfamily.meow.ui.theme.dz
import com.myfamily.meow.ui.theme.sz
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Figma 내역 frame: the daily swipe review. */
@Composable
fun ReviewScreen(onDone: () -> Unit) {
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
    var settling by remember { mutableStateOf<ExpenseTransaction?>(null) }
    var command by remember { mutableStateOf<SwipeAction?>(null) }
    var resetKey by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(Background)) {
        val list = pending
        when {
            list == null -> Unit
            list.isNotEmpty() -> {
                val top = list.first()
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(50.dz))
                    Wordmark(fontPx = 35)
                    Spacer(Modifier.height(20.dz))
                    Text("남은 내역: ${list.size}건", color = TextSecondary, fontSize = 23.sz)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(width = 321.dz, height = 370.dz)) {
                        if (list.size > 1) {
                            Box(Modifier.fillMaxSize().designCard(RoundedCornerShape(10.dz), 6.dz))
                        }
                        SwipeCard(
                            transaction = top,
                            aiRunning = top.id in aiRunningIds,
                            command = command,
                            resetKey = resetKey,
                            onSwiped = { action ->
                                command = null
                                when (action) {
                                    SwipeAction.INCLUDE -> vm.decide(top, include = true)
                                    SwipeAction.EXCLUDE -> vm.decide(top, include = false)
                                    SwipeAction.SETTLE -> settling = top
                                }
                            },
                            onClick = { editing = top },
                        )
                    }
                    Spacer(Modifier.height(35.dz))
                    CatFace(103.dz)
                    Text("스와이프해라냥!", color = TextPrimary, fontSize = 18.sz, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    ActionBar(onAction = { if (command == null) command = it })
                    Spacer(Modifier.height(24.dz))
                }
            }
            reviewedToday.isNotEmpty() && !summaryDismissed -> ReviewSummary(
                reviewed = reviewedToday,
                onAdd = { adding = true },
                onFinish = {
                    vm.finish()
                    onDone()
                },
            )
            else -> EmptyReview(onAdd = { adding = true }, onHome = onDone)
        }

        lastSwiped?.let { swiped ->
            LaunchedEffect(swiped) {
                delay(4_000)
                vm.clearUndo()
            }
            UndoBar(
                text = "${swiped.snapshot.merchant} " + when (swiped.action) {
                    SwipeAction.INCLUDE -> "반영했어요"
                    SwipeAction.EXCLUDE -> "제외했어요"
                    SwipeAction.SETTLE -> "정산했어요"
                },
                onUndo = vm::undo,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dz, start = 20.dz, end = 20.dz),
            )
        }
    }

    settling?.let { tx ->
        SettleDialog(
            transaction = tx,
            onDismiss = {
                settling = null
                resetKey++ // bring the card back
            },
            onConfirm = { people ->
                vm.settle(tx, people)
                settling = null
            },
        )
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

/** Figma bottom bar: 380×56 pill, 34px colored circles with arrows, 14px labels. */
@Composable
private fun ActionBar(onAction: (SwipeAction) -> Unit) {
    Row(
        Modifier
            .size(width = 380.dz, height = 56.dz)
            .designCard(RoundedCornerShape(50.dz), 3.dz),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(SwipeAction.EXCLUDE to "제외", SwipeAction.SETTLE to "정산", SwipeAction.INCLUDE to "반영").forEach { (action, label) ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dz))
                    .clickable { onAction(action) }
                    .padding(horizontal = 8.dz, vertical = 6.dz),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dz).clip(CircleShape).background(action.color), contentAlignment = Alignment.Center) {
                    Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dz).rotate(action.iconRotation))
                }
                Spacer(Modifier.width(8.dz))
                Text(label, color = TextPrimary, fontSize = 14.sz, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Up swipe: how many people shared this payment; only my share is recorded. */
@Composable
private fun SettleDialog(transaction: ExpenseTransaction, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var people by remember { mutableIntStateOf(2) }
    val share = (transaction.amount + people - 1) / people
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dz))
                .background(Color.White)
                .padding(24.dz),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(56.dz).clip(CircleShape).background(SwipeSettle), contentAlignment = Alignment.Center) {
                Icon(
                    SwipeAction.SETTLE.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dz).rotate(SwipeAction.SETTLE.iconRotation),
                )
            }
            Spacer(Modifier.height(12.dz))
            Text("정산하기", color = TextPrimary, fontSize = 26.sz, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dz))
            Text(
                "${transaction.merchant} ${formatWon(transaction.amount)}\n몇 명이서 나눴나요?",
                color = TextSecondary,
                fontSize = 18.sz,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dz))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton("−", enabled = people > 2) { people-- }
                Text(
                    "${people}명",
                    color = TextPrimary,
                    fontSize = 34.sz,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(120.dz),
                )
                StepperButton("+", enabled = people < 30) { people++ }
            }
            Spacer(Modifier.height(16.dz))
            Text("내 몫", color = TextMuted, fontSize = 16.sz)
            Text(formatWon(share), color = SwipeSettle, fontSize = 36.sz, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dz))
            DesignButton("내 몫만 반영하기", { onConfirm(people) }, container = SwipeSettle, content = Color.White, heightPx = 60, fontPx = 20)
            TextButton(onClick = onDismiss) { Text("취소", color = TextMuted, fontSize = 16.sz) }
        }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dz)
            .clip(CircleShape)
            .background(if (enabled) PinkLight else PinkLight.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextPrimary, fontSize = 28.sz, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyReview(onAdd: () -> Unit, onHome: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 34.dz),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Wordmark(fontPx = 35)
        Spacer(Modifier.height(60.dz))
        CatFace(130.dz)
        Spacer(Modifier.height(20.dz))
        Text("새로운 내역이 없어요", color = TextPrimary, fontSize = 30.sz, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dz))
        Text("결제 알림이 오면 여기에 카드로 쌓여요", color = TextSecondary, fontSize = 18.sz)
        Spacer(Modifier.height(40.dz))
        DesignButton("+ 직접 기록하기", onAdd)
        Spacer(Modifier.height(12.dz))
        TextButton(onClick = onHome) { Text("홈으로", color = TextMuted, fontSize = 17.sz) }
    }
}

@Composable
private fun ReviewSummary(reviewed: List<ExpenseTransaction>, onAdd: () -> Unit, onFinish: () -> Unit) {
    val included = reviewed.filter { it.status == TransactionStatus.INCLUDED }
    val excludedCount = reviewed.size - included.size
    val spent = included.filterNot { it.isIncome }.sumOf { it.amount }
    val earned = included.filter { it.isIncome }.sumOf { it.amount }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dz),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dz))
        CatFace(110.dz)
        Text("오늘의 검토 완료!", color = TextPrimary, fontSize = 30.sz, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dz))
        Text(formatWon(spent), color = Pink, fontSize = 44.sz, fontWeight = FontWeight.Bold)
        if (earned > 0) Text("수입 +${formatWon(earned)}", color = Sky, fontSize = 20.sz, fontWeight = FontWeight.SemiBold)
        Text("${included.size}건 반영 · ${excludedCount}건 제외", color = TextSecondary, fontSize = 18.sz)
        Spacer(Modifier.height(24.dz))
        included.forEach { tx ->
            HistoryRow(tx, onClick = null)
            Spacer(Modifier.height(14.dz))
        }
        Spacer(Modifier.height(10.dz))
        DesignButton("+ 직접 기록하기", onAdd, container = Color.White)
        Spacer(Modifier.height(12.dz))
        DesignButton("저장하고 마치기", onFinish, container = Pink, content = Color.White)
        Spacer(Modifier.height(32.dz))
    }
}

@Composable
private fun UndoBar(text: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .designCard(RoundedCornerShape(30.dz), 4.dz)
            .padding(start = 20.dz, end = 6.dz),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = TextPrimary, fontSize = 16.sz, maxLines = 1, modifier = Modifier.weight(1f))
        TextButton(onClick = onUndo) { Text("실행 취소", color = Pink, fontSize = 16.sz, fontWeight = FontWeight.Bold) }
    }
}
