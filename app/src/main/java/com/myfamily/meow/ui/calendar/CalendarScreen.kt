package com.myfamily.meow.ui.calendar

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.myfamily.meow.repository
import com.myfamily.meow.ui.common.TransactionEditDialog
import com.myfamily.meow.ui.common.formatCompact
import com.myfamily.meow.ui.common.formatTime
import com.myfamily.meow.ui.common.formatWon
import com.myfamily.meow.ui.theme.AmountStyle
import com.myfamily.meow.ui.theme.Mint
import com.myfamily.meow.ui.theme.Outline
import com.myfamily.meow.ui.theme.Surface
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: CalendarViewModel = viewModel(factory = viewModelFactory {
        initializer { CalendarViewModel(context.repository) }
    })
    val month by vm.month.collectAsStateWithLifecycle()
    val byDay by vm.byDay.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        MonthHeader(month, byDay.values.sumOf { day -> day.sumOf { it.amount } }, vm::previousMonth, vm::nextMonth)
        Spacer(Modifier.height(24.dp))
        MonthGrid(month, byDay, selected, onSelect = { vm.select(if (it == selected) null else it) })

        selected?.let { date ->
            Spacer(Modifier.height(16.dp))
            DayDetail(
                date = date,
                transactions = byDay[date].orEmpty(),
                onAdd = { adding = true },
                onClose = { vm.select(null) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (adding) {
        TransactionEditDialog(
            initial = null,
            date = selected ?: LocalDate.now(),
            onDismiss = { adding = false },
            onSave = {
                vm.addManual(it)
                adding = false
            },
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, total: Long, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", tint = TextSecondary)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${month.year}년 ${month.monthValue}월", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("총 ${formatWon(total)}", color = Mint, fontSize = 16.sp, style = AmountStyle)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", tint = TextSecondary)
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    byDay: Map<LocalDate, List<ExpenseTransaction>>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    // Sunday-first: DayOfWeek.value is 1 (Mon)..7 (Sun).
    val leadingBlanks = month.atDay(1).dayOfWeek.value % 7
    val cells = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Row(Modifier.fillMaxWidth()) {
        listOf("일", "월", "화", "수", "목", "금", "토").forEach {
            Text(it, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(12.dp))
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                Box(Modifier.weight(1f)) {
                    if (date != null) {
                        DayCell(
                            date = date,
                            total = byDay[date]?.sumOf { it.amount },
                            isToday = date == today,
                            isSelected = date == selected,
                            onClick = { onSelect(date) },
                        )
                    }
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, total: Long?, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = if (isToday || isSelected) TextPrimary else TextSecondary,
            fontSize = 17.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(4.dp))
        if (total != null) {
            Box(
                Modifier
                    .width(20.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Mint),
            )
            Text(formatCompact(total), color = Mint, fontSize = 10.sp)
        } else {
            Spacer(Modifier.size(width = 20.dp, height = 5.dp + 14.dp))
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    transactions: List<ExpenseTransaction>,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d일 소비 내역".format(date.dayOfMonth),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("닫기", color = TextSecondary) }
        }
        if (transactions.isEmpty()) {
            Text("기록된 소비가 없어요", color = TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
        }
        transactions.forEach { tx ->
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tx.merchant, color = TextPrimary, fontSize = 17.sp)
                    Text(
                        "${formatTime(tx.transactionTime).takeLast(5)} · ${tx.sourceLabel} · ${tx.category.label}",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                }
                Text(formatWon(tx.amount), color = TextPrimary, style = AmountStyle)
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Outline)
        )
        TextButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("+ 이 날짜에 직접 기록하기", color = TextSecondary)
        }
        Text(
            "합계 ${formatWon(transactions.sumOf { it.amount })}",
            color = Mint,
            style = AmountStyle,
            modifier = Modifier.align(Alignment.End),
        )
    }
}
