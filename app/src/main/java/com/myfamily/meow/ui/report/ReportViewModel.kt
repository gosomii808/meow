package com.myfamily.meow.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfamily.meow.analysis.AnomalyDetector
import com.myfamily.meow.analysis.RecurringDetector
import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.TransactionStatus
import com.myfamily.meow.data.repository.TransactionRepository
import com.myfamily.meow.ui.common.isIncome
import com.myfamily.meow.ui.common.startMillis
import com.myfamily.meow.ui.common.toLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.YearMonth

data class MonthSpending(
    val month: YearMonth = YearMonth.now(),
    val total: Long = 0,
    val byCategory: Map<Category, Long> = emptyMap(),
    /** Spending per day of month, index 0 = day 1. */
    val daily: List<Long> = emptyList(),
    /**
     * Fraction (0..1) of the monthly goal that the goal line should reach by the END of each
     * day, shaped by the user's weekday spending pattern. Last value is 1.0. Uniform (straight
     * line) when there's no history. Multiply by the goal to get the goal-pace curve.
     */
    val goalShape: List<Float> = emptyList(),
)

/** This month and the previous [MONTHS_BACK], newest first, for the swipeable report. */
class ReportViewModel(repository: TransactionRepository) : ViewModel() {
    private val current = YearMonth.now()
    private val shown = (0..MONTHS_BACK).map { current.minusMonths(it.toLong()) } // newest first

    val months: StateFlow<List<MonthSpending>> = repository
        .reviewedBetween(current.minusMonths(MONTHS_BACK.toLong()).atDay(1).startMillis(), current.plusMonths(1).atDay(1).startMillis())
        .map { rows ->
            val spent = rows.filter { it.status == TransactionStatus.INCLUDED && !it.isIncome }
            val weekday = weekdayWeights(spent)
            shown.map { ym -> monthSpending(spent, ym, weekday) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), shown.map { MonthSpending(it) })

    /** Recurring/subscription payments over a wider window (spec C); display only. */
    val recurring: StateFlow<List<RecurringDetector.RecurringPayment>> = repository
        .reviewedBetween(current.minusMonths(RECURRING_MONTHS).atDay(1).startMillis(), current.plusMonths(1).atDay(1).startMillis())
        .map { RecurringDetector.detect(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Unusual-spike categories this week versus their own history (spec D); display only. */
    val anomalies: StateFlow<List<AnomalyDetector.Anomaly>> = repository
        .reviewedBetween(current.minusMonths(RECURRING_MONTHS).atDay(1).startMillis(), current.plusMonths(1).atDay(1).startMillis())
        .map { AnomalyDetector.detect(it, java.time.LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Average spend per weekday from history, normalized so the mean is 1 (uniform if empty). */
    private fun weekdayWeights(spent: List<com.myfamily.meow.data.entity.ExpenseTransaction>): Map<DayOfWeek, Float> {
        if (spent.isEmpty()) return DayOfWeek.entries.associateWith { 1f }
        val byDate = spent.groupBy { it.transactionTime.toLocalDate() }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val dates = byDate.keys
        val start = dates.min()
        val end = dates.max()
        val perWeekdayTotal = DoubleArray(7)
        val perWeekdayDays = IntArray(7)
        var d = start
        while (!d.isAfter(end)) {
            val i = d.dayOfWeek.ordinal
            perWeekdayTotal[i] += (byDate[d] ?: 0L).toDouble()
            perWeekdayDays[i] += 1
            d = d.plusDays(1)
        }
        val avg = DayOfWeek.entries.associateWith { dow ->
            val i = dow.ordinal
            if (perWeekdayDays[i] == 0) 0.0 else perWeekdayTotal[i] / perWeekdayDays[i]
        }
        val mean = avg.values.average().takeIf { it > 0 } ?: return DayOfWeek.entries.associateWith { 1f }
        return avg.mapValues { (it.value / mean).toFloat().coerceAtLeast(0.1f) }
    }

    private fun monthSpending(
        spent: List<com.myfamily.meow.data.entity.ExpenseTransaction>,
        ym: YearMonth,
        weekday: Map<DayOfWeek, Float>,
    ): MonthSpending {
        val rows = spent.filter { YearMonth.from(it.transactionTime.toLocalDate()) == ym }
        val len = ym.lengthOfMonth()
        val daily = LongArray(len)
        rows.forEach { daily[it.transactionTime.toLocalDate().dayOfMonth - 1] += it.amount }

        // Goal pace: distribute the goal across days by each day's weekday weight.
        val dayWeights = (1..len).map { weekday[ym.atDay(it).dayOfWeek] ?: 1f }
        val totalWeight = dayWeights.sum().takeIf { it > 0 } ?: len.toFloat()
        var acc = 0f
        val shape = dayWeights.map { w -> acc += w; acc / totalWeight }

        return MonthSpending(
            month = ym,
            total = rows.sumOf { it.amount },
            byCategory = rows.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } },
            daily = daily.toList(),
            goalShape = shape,
        )
    }

    private companion object {
        const val MONTHS_BACK = 2 // current + 2 previous = 3 months
        const val RECURRING_MONTHS = 6L // wider window so monthly subscriptions reach 3+ payments
    }
}
