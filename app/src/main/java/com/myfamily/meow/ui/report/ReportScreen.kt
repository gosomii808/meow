package com.myfamily.meow.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myfamily.meow.classification.Category
import com.myfamily.meow.repository
import com.myfamily.meow.ui.common.BrandLockup
import com.myfamily.meow.ui.common.DesignButton
import com.myfamily.meow.ui.common.formatWon
import com.myfamily.meow.ui.theme.Background
import com.myfamily.meow.ui.theme.DotInactive
import com.myfamily.meow.ui.theme.Pink
import com.myfamily.meow.ui.theme.PinkSoft
import com.myfamily.meow.ui.theme.SpeedBg
import com.myfamily.meow.ui.theme.SpeedBorder
import com.myfamily.meow.ui.theme.SwipeExclude
import com.myfamily.meow.ui.theme.SwipeInclude
import com.myfamily.meow.ui.theme.TextMuted
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary
import com.myfamily.meow.ui.theme.color
import com.myfamily.meow.ui.theme.dz
import com.myfamily.meow.ui.theme.sz
import java.time.LocalDate
import java.time.YearMonth

private val GoalCardBg = Color(0xFFFDF1F3)
private val GoalCardBorder = Color(0xFFF2D3D9)
private val DonutPink = Color(0xFFE28393)
private val GoalLine = Color(0xFFD97783)
private val ActualLine = Color(0xFF3DA35D)

@Composable
fun reportViewModel(): ReportViewModel {
    val context = LocalContext.current
    return viewModel(factory = viewModelFactory { initializer { ReportViewModel(context.repository) } })
}

/** Figma 분석 리포트 frame. The design's charts are images; here they are drawn from real data. */
@Composable
fun ReportScreen(onHome: () -> Unit, onEditGoals: () -> Unit, onChat: () -> Unit) {
    val context = LocalContext.current
    val vm = reportViewModel()
    val months by vm.months.collectAsStateWithLifecycle()
    val goals = remember { GoalSettings(context) }
    var monthlyGoal by remember { mutableStateOf(goals.monthly) }
    var categoryGoals by remember { mutableStateOf(goals.categoryGoals()) }
    LifecycleResumeEffect(Unit) {
        monthlyGoal = goals.monthly
        categoryGoals = goals.categoryGoals()
        onPauseOrDispose { }
    }
    // months is newest-first; show oldest→newest so swiping left moves forward in time.
    val ordered = months.asReversed()
    val pager = rememberPagerState(initialPage = (ordered.size - 1).coerceAtLeast(0)) { ordered.size }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 27.dz),
    ) {
        Spacer(Modifier.height(13.dz))
        BrandLockup(onClick = onHome)
        Spacer(Modifier.height(30.dz))
        Text(
            "소비 분석 리포트",
            color = TextPrimary,
            fontSize = 40.sz,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dz))
        MonthSwitcher(ordered, pager)
        Spacer(Modifier.height(16.dz))
        HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { page ->
            val spending = ordered[page]
            Column {
                GoalCard(spending, monthlyGoal, categoryGoals, onEditGoals)
                Spacer(Modifier.height(26.dz))
                SpeedCard(spending, monthlyGoal)
            }
        }
        Spacer(Modifier.height(20.dz))
        DesignButton("고양이에게 이 리포트 물어보기 🐾", onChat, container = PinkSoft, heightPx = 64, fontPx = 19)
        Spacer(Modifier.height(40.dz))
    }
}

/** Month title with arrows + dots; swipe the pager or tap the arrows. */
@Composable
private fun MonthSwitcher(months: List<MonthSpending>, pager: PagerState) {
    val scope = rememberCoroutineScope()
    val page = pager.currentPage.coerceIn(0, (months.size - 1).coerceAtLeast(0))
    val month = months.getOrNull(page)?.month ?: YearMonth.now()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { scope.launch { pager.animateScrollToPage((page - 1).coerceAtLeast(0)) } },
            enabled = page > 0,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "이전 달",
                tint = if (page > 0) TextPrimary else DotInactive,
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${month.year}년 ${month.monthValue}월", color = TextPrimary, fontSize = 22.sz, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dz))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dz)) {
                months.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == page) 9.dz else 7.dz)
                            .clip(CircleShape)
                            .background(if (i == page) Pink else DotInactive),
                    )
                }
            }
        }
        IconButton(
            onClick = { scope.launch { pager.animateScrollToPage((page + 1).coerceAtMost(months.size - 1)) } },
            enabled = page < months.size - 1,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "다음 달",
                tint = if (page < months.size - 1) TextPrimary else DotInactive,
            )
        }
    }
}

@Composable
private fun GoalCard(spending: MonthSpending, goal: Long, categoryGoals: Map<Category, Long>, onEditGoals: () -> Unit) {
    val month = spending.month.monthValue
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dz))
            .background(GoalCardBg)
            .border(1.dz, GoalCardBorder, RoundedCornerShape(12.dz))
            .padding(horizontal = 18.dz, vertical = 18.dz),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Donut(ratio = if (goal > 0) spending.total.toFloat() / goal else 0f, modifier = Modifier.size(128.dz))
            Spacer(Modifier.width(18.dz))
            Column(verticalArrangement = Arrangement.spacedBy(4.dz)) {
                Text("소비 목표 관리", color = TextPrimary, fontSize = 21.sz, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dz))
                if (goal > 0) {
                    val left = goal - spending.total
                    GoalLine("${month}월 소비 목표: ${formatWon(goal)}")
                    GoalLine("현재 소비: ${formatWon(spending.total)}")
                    if (left >= 0) GoalLine("잔여 목표: ${formatWon(left)}") else GoalLine("초과: ${formatWon(-left)}", SwipeExclude)
                } else {
                    GoalLine("${month}월 목표가 아직 없어요")
                    GoalLine("현재 소비: ${formatWon(spending.total)}")
                }
            }
        }
        Spacer(Modifier.height(20.dz))

        // Up to three categories: ones with goals first, then the biggest spenders.
        val shown = (categoryGoals.keys.sortedByDescending { spending.byCategory[it] ?: 0 } +
            spending.byCategory.entries.sortedByDescending { it.value }.map { it.key })
            .distinct()
            .take(3)
        if (shown.isEmpty()) {
            Text("반영된 소비가 생기면 카테고리별로 보여드릴게요", color = TextMuted, fontSize = 15.sz)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dz)) {
                shown.forEach { c ->
                    CategoryBars(c, spent = spending.byCategory[c] ?: 0, goal = categoryGoals[c], modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(20.dz))
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dz)
                .clip(RoundedCornerShape(12.dz))
                .background(Brush.horizontalGradient(listOf(Pink, DonutPink)))
                .clickable(onClick = onEditGoals),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (goal > 0) "목표 설정 및 수정하기" else "목표 설정하기", color = Color.White, fontSize = 18.sz, fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(22.dz))
        }
    }
}

@Composable
private fun GoalLine(text: String, color: Color = TextPrimary) {
    Text(text, color = color, fontSize = 17.sz, fontWeight = FontWeight.SemiBold)
}

/** Ring of spent/goal around a paw mark. */
@Composable
private fun Donut(ratio: Float, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.17f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(DotInactive, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        if (ratio > 0f) {
            drawArc(
                if (ratio > 1f) SwipeExclude else DonutPink,
                -90f,
                360f * ratio.coerceAtMost(1f),
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        // Paw: pad + four toes in a thin pink circle.
        val c = center
        val r = size.minDimension * 0.24f
        drawCircle(DonutPink, r, c, style = Stroke(size.minDimension * 0.025f))
        drawOval(DonutPink, Offset(c.x - r * 0.42f, c.y - r * 0.05f), Size(r * 0.84f, r * 0.62f))
        listOf(-0.55f to -0.2f, -0.2f to -0.52f, 0.2f to -0.52f, 0.55f to -0.2f).forEach { (dx, dy) ->
            drawCircle(DonutPink, r * 0.16f, Offset(c.x + dx * r, c.y + dy * r))
        }
    }
}

/** Goal (gray) vs spent (category color) bars, as in the design's three mini charts. */
@Composable
private fun CategoryBars(category: Category, spent: Long, goal: Long?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(category.label, color = TextPrimary, fontSize = 16.sz, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dz))
        val max = maxOf(spent, goal ?: 0, 1)
        Canvas(Modifier.fillMaxWidth().height(62.dz)) {
            val barW = size.width * 0.22f
            val gap = size.width * 0.12f
            val left = (size.width - barW * 2 - gap) / 2
            drawLine(DotInactive, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            fun bar(x: Float, value: Long, color: Color) {
                val h = size.height * value / max
                drawRoundRect(color, Offset(x, size.height - h), Size(barW, h), CornerRadius(barW * 0.25f))
            }
            if (goal != null) bar(left, goal, DotInactive)
            bar(if (goal != null) left + barW + gap else (size.width - barW) / 2, spent, category.color)
        }
        Spacer(Modifier.height(4.dz))
        Text(
            if (goal != null) {
                val left = goal - spent
                if (left >= 0) "잔여: ${formatWon(left)}" else "초과: ${formatWon(-left)}"
            } else {
                "사용: ${formatWon(spent)}"
            },
            color = if (goal != null && spent > goal) SwipeExclude else TextSecondary,
            fontSize = 12.sz,
        )
    }
}

/** Cumulative spending this month vs. an even pace toward the goal. */
/** Smallest clean step whose 4× covers [max], for readable won axis labels. */
private fun niceStep(max: Long): Long {
    val steps = longArrayOf(
        10_000, 20_000, 25_000, 50_000, 100_000, 150_000, 200_000, 250_000,
        500_000, 750_000, 1_000_000, 1_500_000, 2_000_000, 5_000_000, 10_000_000,
    )
    val target = (max + 3) / 4
    return steps.firstOrNull { it >= target } ?: ((target + 999_999) / 1_000_000 * 1_000_000)
}

@Composable
private fun SpeedCard(spending: MonthSpending, goal: Long) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = TextMuted, fontSize = 11.sz)
    val days = spending.daily.size
    val today = LocalDate.now()
    val shownDays = if (spending.month.year == today.year && spending.month.month == today.month) today.dayOfMonth else days
    val cumulative = spending.daily.runningReduce { a, b -> a + b }.take(shownDays)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dz))
            .background(SpeedBg)
            .border(2.dz, SpeedBorder, RoundedCornerShape(10.dz))
            .padding(14.dz),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("소비 속도 분석", color = TextPrimary, fontSize = 21.sz, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (goal > 0) Legend(GoalLine, "목표 소비 속도")
            Spacer(Modifier.width(10.dz))
            Legend(ActualLine, "현재 누적 소비")
        }
        Spacer(Modifier.height(10.dz))
        val axisHeight = 18.dz
        // Round the top of the axis to a clean step so the won labels read nicely.
        val rawMax = maxOf(goal, cumulative.maxOrNull() ?: 0L, 10_000L)
        val step = niceStep(rawMax)
        val yMax = (step * 4).toFloat()
        val ticks = (0..4).map { it * step }
        // Left margin wide enough for the full-won labels (no "k"/"만" abbreviation).
        val density = LocalDensity.current
        val labelMaxPx = ticks.maxOf { measurer.measure(formatWon(it), labelStyle).size.width }
        val axisWidth = with(density) { labelMaxPx.toDp() } + 8.dz
        Canvas(Modifier.fillMaxWidth().height(170.dz)) {
            val axisW = axisWidth.toPx()
            val axisH = axisHeight.toPx()
            val w = size.width - axisW
            val h = size.height - axisH
            fun x(day: Int) = axisW + w * (day - 1) / (days - 1).coerceAtLeast(1)
            fun y(v: Float) = h - h * v / yMax

            ticks.forEach { v ->
                val gy = y(v.toFloat())
                drawLine(Color(0x22000000), Offset(axisW, gy), Offset(size.width, gy), 1f)
                val label = measurer.measure(formatWon(v), labelStyle)
                drawText(label, topLeft = Offset(axisW - label.size.width - 6f, gy - label.size.height / 2))
            }
            listOf(1, days / 4, days / 2, days * 3 / 4, days).distinct().filter { it >= 1 }.forEach { d ->
                val label = measurer.measure("${d}일", labelStyle)
                drawText(label, topLeft = Offset(x(d) - label.size.width / 2, h + 4f))
            }
            if (goal > 0 && spending.goalShape.isNotEmpty()) {
                // Goal pace shaped by the user's weekday spending pattern (steeper on heavy days).
                val gp = Path().apply {
                    moveTo(x(1), y(0f))
                    for (d in 1..days) lineTo(x(d), y(goal * spending.goalShape[d - 1]))
                }
                drawPath(gp, GoalLine, style = Stroke(5f, cap = StrokeCap.Round))
            }
            if (cumulative.isNotEmpty()) {
                val line = Path()
                cumulative.forEachIndexed { i, v ->
                    if (i == 0) line.moveTo(x(1), y(v.toFloat())) else line.lineTo(x(i + 1), y(v.toFloat()))
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(cumulative.size), h)
                    lineTo(x(1), h)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(ActualLine.copy(alpha = 0.35f), Color.Transparent), 0f, h))
                drawPath(line, ActualLine, style = Stroke(5f, cap = StrokeCap.Round))
            }
        }
        if (goal > 0 && cumulative.isNotEmpty()) {
            // Expected spend by today, following the weekday-shaped goal curve.
            val pace = (goal * (spending.goalShape.getOrNull(cumulative.size - 1) ?: (cumulative.size.toFloat() / days))).toLong()
            val diff = cumulative.last() - pace
            Spacer(Modifier.height(8.dz))
            Text(
                if (diff > 0) "목표 속도보다 ${formatWon(diff)} 빠르게 쓰고 있어요" else "목표 속도보다 ${formatWon(-diff)} 여유 있어요",
                color = if (diff > 0) SwipeExclude else SwipeInclude,
                fontSize = 15.sz,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 16.dz, height = 3.dz).background(color))
        Spacer(Modifier.width(4.dz))
        Text(label, color = TextPrimary, fontSize = 12.sz, fontWeight = FontWeight.SemiBold)
    }
}
