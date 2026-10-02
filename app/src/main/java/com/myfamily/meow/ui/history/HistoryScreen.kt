package com.myfamily.meow.ui.history

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import com.myfamily.meow.repository
import com.myfamily.meow.ui.calendar.CalendarViewModel
import com.myfamily.meow.ui.common.BrandLockup
import com.myfamily.meow.ui.common.CategoryPill
import com.myfamily.meow.ui.common.EditAction
import com.myfamily.meow.ui.common.Pill
import com.myfamily.meow.ui.common.TransactionEditDialog
import com.myfamily.meow.ui.common.designCard
import com.myfamily.meow.ui.common.formatCompact
import com.myfamily.meow.ui.common.formatWon
import com.myfamily.meow.ui.common.isIncome
import com.myfamily.meow.ui.common.signedWon
import com.myfamily.meow.ui.theme.Background
import com.myfamily.meow.ui.theme.Pink
import com.myfamily.meow.ui.theme.PinkLight
import com.myfamily.meow.ui.theme.PinkSoft
import com.myfamily.meow.ui.theme.Sky
import com.myfamily.meow.ui.theme.SwipeExclude
import com.myfamily.meow.ui.theme.SwipeSettle
import com.myfamily.meow.ui.theme.TextMuted
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary
import com.myfamily.meow.ui.theme.dz
import com.myfamily.meow.ui.theme.sz
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val List<ExpenseTransaction>.included get() = filter { it.status == TransactionStatus.INCLUDED }
private val List<ExpenseTransaction>.spent get() = included.filterNot { it.isIncome }.sumOf { it.amount }
private val List<ExpenseTransaction>.earned get() = included.filter { it.isIncome }.sumOf { it.amount }

/** Figma 내역 확인 frame ("소비 내역"), plus the calendar the owner asked to keep. */
@Composable
fun HistoryScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val vm: CalendarViewModel = viewModel(factory = viewModelFactory { initializer { CalendarViewModel(context.repository) } })
    val month by vm.month.collectAsStateWithLifecycle()
    val byDay by vm.byDay.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    var calendarMode by rememberSaveable { mutableStateOf(false) }
    var showExcluded by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseTransaction?>(null) }
    var adding by remember { mutableStateOf(false) }

    val all = byDay.values.flatten()
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dz),
    ) {
        Spacer(Modifier.height(13.dz))
        BrandLockup(Modifier.padding(start = 0.dz), onClick = onHome)
        Spacer(Modifier.height(30.dz))
        Text(
            "소비 내역",
            color = TextPrimary,
            fontSize = 40.sz,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dz))
        ModeToggle(calendarMode, onChange = { calendarMode = it })
        Spacer(Modifier.height(12.dz))
        MonthHeader(month, all.spent, all.earned, vm::previousMonth, vm::nextMonth)
        Spacer(Modifier.height(20.dz))

        if (calendarMode) {
            MonthGrid(month, byDay, selected, onSelect = { vm.select(if (it == selected) null else it) })
            selected?.let { date ->
                Spacer(Modifier.height(16.dz))
                DayDetail(date, byDay[date].orEmpty(), onEdit = { editing = it }, onAdd = { adding = true }, onClose = { vm.select(null) })
            }
        } else {
            val days = byDay.keys.sortedDescending()
            val visible = days.mapNotNull { d ->
                val rows = byDay[d].orEmpty()
                    .filter { showExcluded || it.status == TransactionStatus.INCLUDED }
                    .sortedByDescending { it.transactionTime } // newest first within the day
                if (rows.isEmpty()) null else d to rows
            }
            if (visible.isEmpty()) {
                Text(
                    "이번 달 반영된 내역이 없어요",
                    color = TextMuted,
                    fontSize = 18.sz,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dz),
                )
            }
            visible.forEach { (date, rows) ->
                Text(dayLabel(date), color = TextSecondary, fontSize = 17.sz, modifier = Modifier.padding(start = 4.dz, bottom = 10.dz))
                rows.forEach { tx ->
                    HistoryRow(tx, onClick = { editing = tx })
                    Spacer(Modifier.height(23.dz))
                }
            }
            val excludedCount = all.count { it.status == TransactionStatus.EXCLUDED }
            if (excludedCount > 0) {
                TextButton(onClick = { showExcluded = !showExcluded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("제외한 내역 ${excludedCount}건 ${if (showExcluded) "숨기기" else "보기"}", color = TextMuted, fontSize = 16.sz)
                }
            }
            TextButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("+ 직접 기록하기", color = TextSecondary, fontSize = 17.sz)
            }
        }
        Spacer(Modifier.height(32.dz))
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
    editing?.let { tx ->
        val close = { editing = null }
        val actions = buildList {
            if (tx.status == TransactionStatus.INCLUDED) {
                add(EditAction("제외하기 (합계에서 빼기)", SwipeExclude) { vm.setStatus(tx, TransactionStatus.EXCLUDED); close() })
            } else {
                add(EditAction("다시 포함하기") { vm.setStatus(tx, TransactionStatus.INCLUDED); close() })
            }
            if (tx.source == TransactionSource.MANUAL) add(EditAction("삭제", SwipeExclude) { vm.delete(tx); close() })
        }
        TransactionEditDialog(
            initial = tx,
            date = selected ?: LocalDate.now(),
            onDismiss = close,
            onSave = {
                vm.saveEdit(tx, it)
                close()
            },
            actions = actions,
        )
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("M월 d일")

private fun dayLabel(date: LocalDate): String =
    "${date.format(dayFormat)} (${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

/**
 * Figma history card: 393×137, radius 20, shadow 2/2/4; chip 89×39 + merchant (23, gray);
 * card name (30, medium) and amount (30, bold) below.
 */
@Composable
fun HistoryRow(tx: ExpenseTransaction, onClick: (() -> Unit)?) {
    val excluded = tx.status == TransactionStatus.EXCLUDED
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (excluded) 0.5f else 1f)
            .designCard(RoundedCornerShape(20.dz), 3.dz)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 30.dz, end = 26.dz, top = 20.dz, bottom = 22.dz),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tx.isIncome) Pill("수입", Sky) else CategoryPill(tx.category)
            Spacer(Modifier.width(15.dz))
            Text(
                tx.merchant,
                color = TextSecondary,
                fontSize = 23.sz,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dz))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tx.sourceLabel,
                color = TextPrimary,
                fontSize = 30.sz,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                tx.signedWon(),
                color = if (tx.isIncome) Sky else TextPrimary,
                fontSize = 30.sz,
                fontWeight = FontWeight.Bold,
                textDecoration = if (excluded) TextDecoration.LineThrough else null,
            )
        }
        if (tx.splitCount != null && tx.originalAmount != null) {
            Text(
                "정산 ${formatWon(tx.originalAmount)} ÷ ${tx.splitCount}명",
                color = SwipeSettle,
                fontSize = 15.sz,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun ModeToggle(calendar: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 90.dz)
            .clip(RoundedCornerShape(20.dz))
            .background(PinkSoft)
            .padding(4.dz),
    ) {
        listOf(false to "목록", true to "캘린더").forEach { (value, label) ->
            Text(
                label,
                color = if (calendar == value) TextPrimary else TextMuted,
                fontSize = 17.sz,
                fontWeight = if (calendar == value) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dz))
                    .background(if (calendar == value) Color.White else Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(vertical = 6.dz),
            )
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, spent: Long, earned: Long, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", tint = TextSecondary)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${month.year}년 ${month.monthValue}월", color = TextPrimary, fontSize = 22.sz, fontWeight = FontWeight.Bold)
            Text("지출 ${formatWon(spent)}", color = Pink, fontSize = 18.sz, fontWeight = FontWeight.SemiBold)
            if (earned > 0) Text("수입 +${formatWon(earned)}", color = Sky, fontSize = 15.sz)
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
    val leadingBlanks = month.atDay(1).dayOfWeek.value % 7 // Sunday-first
    val cells = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(
        Modifier
            .fillMaxWidth()
            .designCard(RoundedCornerShape(20.dz), 3.dz)
            .padding(horizontal = 10.dz, vertical = 16.dz),
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                Text(
                    d,
                    color = when (i) { 0 -> SwipeExclude; 6 -> SwipeSettle; else -> TextMuted },
                    fontSize = 15.sz,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dz))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f)) {
                        if (date != null) {
                            val day = byDay[date].orEmpty()
                            DayCell(date, day.spent, day.earned, date == today, date == selected) { onSelect(date) }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, spent: Long, earned: Long, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dz))
            .background(if (isSelected) PinkLight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dz),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = if (isToday) Pink else TextPrimary,
            fontSize = 17.sz,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Text(if (spent > 0) formatCompact(spent) else "", color = Pink, fontSize = 11.sz)
        Text(if (earned > 0) "+${formatCompact(earned)}" else "", color = Sky, fontSize = 11.sz)
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    transactions: List<ExpenseTransaction>,
    onEdit: (ExpenseTransaction) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dz)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayLabel(date), color = TextPrimary, fontSize = 20.sz, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("닫기", color = TextMuted, fontSize = 15.sz) }
        }
        if (transactions.isEmpty()) {
            Text("기록된 내역이 없어요", color = TextMuted, fontSize = 16.sz)
        }
        transactions
            .sortedWith(compareBy<ExpenseTransaction> { it.status != TransactionStatus.INCLUDED }.thenByDescending { it.transactionTime })
            .forEach { tx -> HistoryRow(tx) { onEdit(tx) } }
        Text(
            "지출 ${formatWon(transactions.spent)}" + if (transactions.earned > 0) " · 수입 +${formatWon(transactions.earned)}" else "",
            color = Pink,
            fontSize = 17.sz,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.End),
        )
        TextButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("+ 이 날짜에 직접 기록하기", color = TextSecondary, fontSize = 16.sz)
        }
    }
}
