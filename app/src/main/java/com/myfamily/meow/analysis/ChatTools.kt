package com.myfamily.meow.analysis

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Function-calling tools for the cat chatbot (spec H / §1-1). The small model only picks a tool
 * and writes sentences; every number is computed here in Kotlin. Kept to 8 tools for small-model
 * reliability. Pure Kotlin so the selection logic and computations are unit-tested.
 *
 * [ChatToolRouter] parses the model's JSON choice and provides a keyword fallback;
 * [ChatToolExecutor] runs a chosen tool against the user's transactions and returns a Korean text
 * result plus the set of numbers that result contains (the allow-list for [NumberVerifier]).
 */
enum class ChatTool(val id: String) {
    SPENDING_SUMMARY("get_spending_summary"),
    COMPARE_PERIODS("compare_periods"),
    TOP_MERCHANTS("get_top_merchants"),
    TIME_PATTERN("get_time_pattern"),
    SIMULATE_WHATIF("simulate_whatif"),
    RECURRING_PAYMENTS("get_recurring_payments"),
    INSIGHTS("get_insights"),
    NONE("none");

    companion object {
        fun from(id: String?): ChatTool? = id?.let { v -> entries.firstOrNull { it.id == v } }
    }
}

data class ToolCall(val tool: ChatTool, val args: Map<String, String> = emptyMap())

data class ToolResult(val text: String, val numbers: Set<String>)

object ChatToolRouter {
    private val TOOL_FIELD = Regex(""""tool"\s*:\s*"([^"]+)"""")
    private val ARG_FIELD = Regex(""""([a-z_]+)"\s*:\s*(?:"([^"]*)"|(-?\d+(?:\.\d+)?))""")

    /**
     * Parses a `{"tool": "...", "args": {...}}` object from the model output (tolerating prose
     * around it). Returns null when no known tool is named.
     */
    fun parse(output: String): ToolCall? {
        val tool = ChatTool.from(TOOL_FIELD.find(output)?.groupValues?.get(1)) ?: return null
        val args = ARG_FIELD.findAll(output)
            .map { it.groupValues[1] to (it.groupValues[2].ifEmpty { it.groupValues[3] }) }
            .filter { it.first != "tool" }
            .toMap()
        return ToolCall(tool, args)
    }

    /** Last-resort keyword routing when the model's JSON is unusable. */
    fun keywordRoute(question: String): ChatTool {
        val q = question.lowercase()
        return when {
            listOf("구독", "고정", "매달", "정기").any { it in q } -> ChatTool.RECURRING_PAYMENTS
            listOf("줄이", "아끼", "절약", "아껴").any { it in q } -> ChatTool.SIMULATE_WHATIF
            listOf("비교", "지난달", "저번달", "전달").any { it in q } -> ChatTool.COMPARE_PERIODS
            listOf("어디", "가게", "많이 간", "자주").any { it in q } -> ChatTool.TOP_MERCHANTS
            listOf("언제", "요일", "시간", "주말", "밤").any { it in q } -> ChatTool.TIME_PATTERN
            listOf("조언", "인사이트", "습관", "어때").any { it in q } -> ChatTool.INSIGHTS
            listOf("얼마", "총", "썼", "지출", "소비").any { it in q } -> ChatTool.SPENDING_SUMMARY
            else -> ChatTool.NONE
        }
    }

    /** Forces a category argument to one of the app's 11 categories, or null. */
    fun coerceCategory(raw: String?): Category? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        return Category.entries.firstOrNull { it.label == s || it.name.equals(s, ignoreCase = true) }
            ?: Category.entries.firstOrNull { it.label in s }
    }
}

class ChatToolExecutor(
    transactions: List<ExpenseTransaction>,
    private val today: LocalDate,
    private val monthlyGoal: Long = 0,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val spent = transactions.filter {
        it.status == TransactionStatus.INCLUDED && it.direction == Direction.EXPENSE
    }
    private val won = NumberFormat.getNumberInstance(Locale.KOREA)
    private fun w(v: Long) = won.format(v) + "원"
    private fun at(tx: ExpenseTransaction) = LocalDateTime.ofInstant(Instant.ofEpochMilli(tx.transactionTime), zone)
    private fun inRange(tx: ExpenseTransaction, start: LocalDate, end: LocalDate): Boolean {
        val d = at(tx).toLocalDate(); return !d.isBefore(start) && !d.isAfter(end)
    }

    fun run(call: ToolCall): ToolResult = when (call.tool) {
        ChatTool.SPENDING_SUMMARY -> summary(startArg(call), endArg(call), cat(call))
        ChatTool.COMPARE_PERIODS -> compare(call)
        ChatTool.TOP_MERCHANTS -> topMerchants(startArg(call), endArg(call), cat(call), limitArg(call))
        ChatTool.TIME_PATTERN -> timePattern(startArg(call), endArg(call), cat(call))
        ChatTool.SIMULATE_WHATIF -> whatIf(cat(call), ratioArg(call))
        ChatTool.RECURRING_PAYMENTS -> recurring()
        ChatTool.INSIGHTS -> insights()
        ChatTool.NONE -> ToolResult("", emptySet())
    }

    private fun summary(start: LocalDate, end: LocalDate, category: Category?): ToolResult {
        val rows = spent.filter { inRange(it, start, end) && (category == null || it.category == category) }
        val total = rows.sumOf { it.amount }
        val numbers = mutableSetOf(w(total), "${rows.size}건")
        val head = if (category != null) "${category.label} ${w(total)}, ${rows.size}건" else "총 ${w(total)}, ${rows.size}건"
        val byCat = if (category == null) {
            rows.groupBy { it.category }.entries.sortedByDescending { it.value.sumOf { r -> r.amount } }.take(5)
                .joinToString(", ") { (c, list) -> val s = list.sumOf { it.amount }; numbers += w(s); "${c.label} ${w(s)}" }
        } else ""
        val text = "[$start~$end] $head" + if (byCat.isNotEmpty()) " / $byCat" else ""
        return ToolResult(text, numbers)
    }

    private fun compare(call: ToolCall): ToolResult {
        val aStart = dateArg(call, "a_start") ?: today.minusMonths(1).withDayOfMonth(1)
        val aEnd = dateArg(call, "a_end") ?: aStart.plusMonths(1).minusDays(1)
        val bStart = dateArg(call, "b_start") ?: today.withDayOfMonth(1)
        val bEnd = dateArg(call, "b_end") ?: today
        val category = cat(call)
        fun sum(s: LocalDate, e: LocalDate) = spent.filter { inRange(it, s, e) && (category == null || it.category == category) }.sumOf { it.amount }
        val a = sum(aStart, aEnd); val b = sum(bStart, bEnd)
        val pct = if (a > 0) ((b - a) * 100.0 / a).roundToLong() else 0
        val label = category?.label ?: "전체"
        val change = when {
            a == 0L -> "이전 기간엔 기록이 없어"
            b >= a -> "${w(b - a)}(${pct}%) 더 씀"
            else -> "${w(a - b)}(${-pct}%) 덜 씀"
        }
        val numbers = setOf(w(a), w(b), "${kotlin.math.abs(pct)}%")
        return ToolResult("[$label] 이전 ${w(a)} → 최근 ${w(b)}, $change", numbers)
    }

    private fun topMerchants(start: LocalDate, end: LocalDate, category: Category?, limit: Int): ToolResult {
        val rows = spent.filter { inRange(it, start, end) && (category == null || it.category == category) }
        val top = rows.groupBy { it.merchant }
            .map { (m, list) -> Triple(m, list.size, list.sumOf { it.amount }) }
            .sortedByDescending { it.third }
            .take(limit.coerceIn(1, 5))
        val numbers = mutableSetOf<String>()
        val text = if (top.isEmpty()) "그 기간엔 기록이 없어" else top.joinToString(", ") {
            numbers += w(it.third); numbers += "${it.second}번"; "${it.first} ${it.second}번 ${w(it.third)}"
        }
        return ToolResult("[자주 간 곳] $text", numbers)
    }

    private fun timePattern(start: LocalDate, end: LocalDate, category: Category?): ToolResult {
        val rows = spent.filter { inRange(it, start, end) && (category == null || it.category == category) }
        val numbers = mutableSetOf<String>()
        val byDay = DayOfWeek.entries.associateWith { d -> rows.filter { at(it).dayOfWeek == d }.sumOf { it.amount } }
        val topDay = byDay.maxByOrNull { it.value }
        val late = rows.filter { val h = at(it).hour; h >= 22 || h < 4 }.sumOf { it.amount }
        val dayLabel = listOf("월", "화", "수", "목", "금", "토", "일")
        val dayText = topDay?.let { numbers += w(it.value); "${dayLabel[it.key.ordinal]}요일에 가장 많이(${w(it.value)})" } ?: "기록 없음"
        numbers += w(late)
        return ToolResult("[소비 시간] $dayText, 밤(22~4시) ${w(late)}", numbers)
    }

    private fun whatIf(category: Category?, reduceRatio: Double): ToolResult {
        if (category == null) return ToolResult("어떤 카테고리를 줄일지 알려줘", emptySet())
        val month = today.withDayOfMonth(1)
        val thisMonthCat = spent.filter { inRange(it, month, today) && it.category == category }.sumOf { it.amount }
        val ratio = reduceRatio.coerceIn(0.0, 1.0)
        val saving = (thisMonthCat * ratio).roundToLong()
        val recurring = RecurringDetector.detect(spent, zone)
        val forecast = MonthForecaster.forecast(spent, today, recurring, zone)
        val projected = if (forecast.hasEnoughData) (forecast.p50 - saving).coerceAtLeast(0) else null
        val numbers = mutableSetOf(w(thisMonthCat), w(saving))
        val tail = if (projected != null) {
            numbers += w(projected)
            ", 이 속도면 월말 약 ${w(projected)}" + if (monthlyGoal > 0) " (목표 ${w(monthlyGoal)})".also { numbers += w(monthlyGoal) } else ""
        } else ""
        val pctLabel = "${(ratio * 100).toInt()}%"; numbers += pctLabel
        return ToolResult("[${category.label} $pctLabel 줄이면] 이번 달 ${w(thisMonthCat)} 중 ${w(saving)} 절약$tail", numbers)
    }

    private fun recurring(): ToolResult {
        val items = RecurringDetector.detect(spent, zone)
        if (items.isEmpty()) return ToolResult("매달 고정으로 나가는 결제는 아직 안 보여", emptySet())
        val monthly = items.sumOf { it.monthlyAmount }
        val numbers = mutableSetOf(w(monthly), "${items.size}건")
        val list = items.take(4).joinToString(", ") { numbers += w(it.monthlyAmount); "${it.merchant} 월 ${w(it.monthlyAmount)}" }
        return ToolResult("[고정 지출] ${items.size}건, 월 ${w(monthly)} ($list)", numbers)
    }

    private fun insights(): ToolResult {
        val recurring = RecurringDetector.detect(spent, zone)
        val anomalies = AnomalyDetector.detect(spent, today, zone)
        val forecast = MonthForecaster.forecast(spent, today, recurring, zone)
        val top = InsightEngine.generate(spent, today, monthlyGoal, recurring, anomalies, forecast, zone)
        if (top.isEmpty()) return ToolResult("아직 눈에 띄는 소비 패턴은 없어", emptySet())
        val numbers = mutableSetOf<String>()
        val text = top.joinToString(" / ") { ins -> numbers += ins.figures; ins.summary + " (" + ins.figures.joinToString(", ") + ")" }
        return ToolResult("[인사이트] $text", numbers)
    }

    // --- argument helpers ---
    private fun cat(call: ToolCall) = ChatToolRouter.coerceCategory(call.args["category"])
    private fun dateArg(call: ToolCall, key: String): LocalDate? =
        call.args[key]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    private fun startArg(call: ToolCall) = dateArg(call, "start_date") ?: today.withDayOfMonth(1)
    private fun endArg(call: ToolCall) = dateArg(call, "end_date") ?: today
    private fun limitArg(call: ToolCall) = call.args["limit"]?.toIntOrNull() ?: 5
    private fun ratioArg(call: ToolCall) = call.args["reduce_ratio"]?.toDoubleOrNull() ?: 0.5
}
