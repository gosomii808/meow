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
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * Rule-based candidate insights, scored and reduced to the top [TOP_N] (spec F / §1-2). The app
 * computes every number here; the chatbot/report only rephrases the summaries in the cat's voice.
 * Pure Kotlin for JUnit.
 *
 * Score = surprise × amount-weight, where surprise is the absolute change ratio versus a baseline
 * (capped at [SURPRISE_CAP]) and amount-weight is log10(amount + 1). Aggregate candidates backed
 * by fewer than [MIN_TX] transactions are dropped, and when two candidates share a category only
 * the higher-scoring one survives.
 */
object InsightEngine {
    private const val TOP_N = 3
    private const val MIN_TX = 3
    private const val SURPRISE_CAP = 3.0
    private const val WINDOW_DAYS = 30L
    private const val SMALL_MAX = 10_000L
    private const val LATE_BASELINE = 0.10
    private val WEEKEND_BASELINE = 2.0 / 7.0

    enum class InsightType { CATEGORY_UP, LATE_NIGHT, WEEKEND, SMALL_FREQUENT, BIGGEST, RECURRING, ANOMALY, FORECAST_OVER }

    data class Insight(
        val type: InsightType,
        val summary: String,
        /** Pre-formatted evidence numbers for display, e.g. "12,000원", "2.3배", "5번". */
        val figures: List<String>,
        val score: Double,
        /** Set for category-scoped insights so duplicates can be collapsed. */
        val category: Category? = null,
    )

    private val won = NumberFormat.getNumberInstance(Locale.KOREA)
    private fun w(v: Long) = won.format(v) + "원"
    private fun times(x: Double) = String.format(Locale.KOREA, "%.1f배", x)
    private fun count(n: Int) = "${n}번"

    private fun surprise(actual: Double, baseline: Double): Double =
        if (baseline <= 0.0) (if (actual > 0) SURPRISE_CAP else 0.0)
        else (abs(actual - baseline) / baseline).coerceAtMost(SURPRISE_CAP)

    private fun amountWeight(amount: Long) = log10(amount.toDouble() + 1)

    fun generate(
        transactions: List<ExpenseTransaction>,
        today: LocalDate,
        monthlyGoal: Long = 0,
        recurring: List<RecurringDetector.RecurringPayment> = emptyList(),
        anomalies: List<AnomalyDetector.Anomaly> = emptyList(),
        forecast: MonthForecaster.Forecast? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Insight> {
        fun at(tx: ExpenseTransaction) = LocalDateTime.ofInstant(Instant.ofEpochMilli(tx.transactionTime), zone)

        val spent = transactions.filter {
            it.status == TransactionStatus.INCLUDED && it.direction == Direction.EXPENSE
        }
        val windowStart = today.minusDays(WINDOW_DAYS - 1)
        val recent = spent.filter { val d = at(it).toLocalDate(); !d.isBefore(windowStart) && !d.isAfter(today) }

        val candidates = mutableListOf<Insight>()
        candidates += categoryIncreases(spent, today, ::at)
        lateNight(recent, ::at)?.let { candidates += it }
        weekend(recent, ::at)?.let { candidates += it }
        smallFrequent(recent)?.let { candidates += it }
        biggest(recent)?.let { candidates += it }
        recurringInsight(recurring)?.let { candidates += it }
        candidates += anomalyInsights(anomalies)
        forecastOver(forecast, monthlyGoal)?.let { candidates += it }

        // Collapse same-category candidates to the highest score, then take the top N overall.
        val deduped = candidates
            .groupBy { it.category }
            .flatMap { (cat, list) -> if (cat == null) list else listOf(list.maxByOrNull { it.score }!!) }
        return deduped.sortedByDescending { it.score }.take(TOP_N)
    }

    private fun categoryIncreases(
        spent: List<ExpenseTransaction>,
        today: LocalDate,
        at: (ExpenseTransaction) -> LocalDateTime,
    ): List<Insight> {
        val last7Start = today.minusDays(6)
        val prior4Start = today.minusDays(34)
        val prior4End = today.minusDays(7)
        val out = mutableListOf<Insight>()
        for (cat in Category.entries) {
            val last7 = spent.filter { it.category == cat && at(it).toLocalDate().let { d -> !d.isBefore(last7Start) && !d.isAfter(today) } }
            if (last7.size < MIN_TX) continue
            val last7Sum = last7.sumOf { it.amount }
            val priorSum = spent.filter { it.category == cat && at(it).toLocalDate().let { d -> !d.isBefore(prior4Start) && !d.isAfter(prior4End) } }.sumOf { it.amount }
            val weeklyAvg = priorSum / 4.0
            if (last7Sum <= weeklyAvg) continue
            val s = surprise(last7Sum.toDouble(), weeklyAvg)
            val ratio = if (weeklyAvg > 0) last7Sum / weeklyAvg else 0.0
            val figures = buildList {
                add(w(last7Sum))
                if (ratio > 0) add(times(ratio))
                add(count(last7.size))
            }
            out += Insight(
                type = InsightType.CATEGORY_UP,
                summary = "최근 7일 ${cat.label} 지출이 평소보다 늘었어요",
                figures = figures,
                score = s * amountWeight(last7Sum),
                category = cat,
            )
        }
        return out
    }

    private fun lateNight(recent: List<ExpenseTransaction>, at: (ExpenseTransaction) -> LocalDateTime): Insight? {
        val total = recent.sumOf { it.amount }
        if (total <= 0) return null
        val late = recent.filter { val h = at(it).hour; h >= 22 || h < 4 }
        if (late.size < MIN_TX) return null
        val lateSum = late.sumOf { it.amount }
        val share = lateSum.toDouble() / total
        if (share <= LATE_BASELINE) return null
        return Insight(
            type = InsightType.LATE_NIGHT,
            summary = "밤 늦게(22~4시) 쓰는 돈이 적지 않아요",
            figures = listOf(w(lateSum), "${(share * 100).toInt()}%", count(late.size)),
            score = surprise(share, LATE_BASELINE) * amountWeight(lateSum),
        )
    }

    private fun weekend(recent: List<ExpenseTransaction>, at: (ExpenseTransaction) -> LocalDateTime): Insight? {
        val total = recent.sumOf { it.amount }
        if (total <= 0) return null
        val weekend = recent.filter { at(it).dayOfWeek == DayOfWeek.SATURDAY || at(it).dayOfWeek == DayOfWeek.SUNDAY }
        if (weekend.size < MIN_TX) return null
        val weekendSum = weekend.sumOf { it.amount }
        val share = weekendSum.toDouble() / total
        if (share <= WEEKEND_BASELINE) return null
        return Insight(
            type = InsightType.WEEKEND,
            summary = "주말에 소비가 몰려 있어요",
            figures = listOf(w(weekendSum), "${(share * 100).toInt()}%", count(weekend.size)),
            score = surprise(share, WEEKEND_BASELINE) * amountWeight(weekendSum),
        )
    }

    private fun smallFrequent(recent: List<ExpenseTransaction>): Insight? {
        val small = recent.filter { it.amount in 1..SMALL_MAX }
        if (small.size < MIN_TX || recent.isEmpty()) return null
        val countShare = small.size.toDouble() / recent.size
        if (countShare <= 0.4) return null
        val smallSum = small.sumOf { it.amount }
        return Insight(
            type = InsightType.SMALL_FREQUENT,
            summary = "만 원 이하 소액 결제가 잦아요",
            figures = listOf(count(small.size), w(smallSum)),
            score = surprise(countShare, 0.4) * amountWeight(smallSum),
        )
    }

    private fun biggest(recent: List<ExpenseTransaction>): Insight? {
        val top = recent.maxByOrNull { it.amount } ?: return null
        val rest = recent.filter { it.id != top.id }
        val avg = if (rest.isEmpty()) 0.0 else rest.sumOf { it.amount }.toDouble() / rest.size
        val s = surprise(top.amount.toDouble(), avg)
        return Insight(
            type = InsightType.BIGGEST,
            summary = "이번 달 가장 큰 한 건은 ${top.merchant}였어요",
            figures = listOf(w(top.amount)),
            score = s * amountWeight(top.amount),
        )
    }

    private fun recurringInsight(recurring: List<RecurringDetector.RecurringPayment>): Insight? {
        if (recurring.isEmpty()) return null
        val monthly = recurring.sumOf { it.monthlyAmount }
        return Insight(
            type = InsightType.RECURRING,
            summary = "매달 자동으로 나가는 고정 지출이 있어요",
            figures = listOf(count(recurring.size), w(monthly)),
            score = 1.5 * amountWeight(monthly),
        )
    }

    private fun anomalyInsights(anomalies: List<AnomalyDetector.Anomaly>): List<Insight> =
        anomalies.map { a ->
            Insight(
                type = InsightType.ANOMALY,
                summary = "이번 주 ${a.category.label}이 평소보다 많아요",
                figures = listOf(w(a.current), times(a.ratio)),
                score = max(a.ratio - 1, 0.0).coerceAtMost(SURPRISE_CAP) * amountWeight(a.current),
                category = a.category,
            )
        }

    private fun forecastOver(forecast: MonthForecaster.Forecast?, goal: Long): Insight? {
        if (forecast == null || !forecast.hasEnoughData || goal <= 0 || forecast.p50 <= goal) return null
        val over = forecast.p50 - goal
        return Insight(
            type = InsightType.FORECAST_OVER,
            summary = "이 속도면 이번 달 목표를 넘길 것 같아요",
            figures = listOf(w(forecast.p50), w(over)),
            score = surprise(forecast.p50.toDouble(), goal.toDouble()) * amountWeight(over),
        )
    }
}
