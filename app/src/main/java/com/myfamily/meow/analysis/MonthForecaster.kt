package com.myfamily.meow.analysis

import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Month-end spending forecast as a P10/P50/P90 range (spec E / §2-3), by bootstrapping each
 * remaining day from past days of the same weekday. Pure Kotlin; [random] is injectable so tests
 * are reproducible.
 *
 * Recurring payments (spec C) are handled separately: their past transactions are removed from
 * the bootstrap pool and their occurrences still due this month are added as fixed amounts, so
 * they aren't double-counted. Needs at least [MIN_DAYS] days of history, else [Forecast.hasEnoughData]
 * is false and the UI shows "데이터를 모으는 중이에요".
 */
object MonthForecaster {
    private const val POOL_DAYS = 60L
    private const val MIN_DAYS = 14L
    private const val ITERATIONS = 1_000

    data class Forecast(
        val hasEnoughData: Boolean,
        /** This month's spending so far (through today). */
        val currentSpent: Long,
        val p10: Long,
        val p50: Long,
        val p90: Long,
    )

    fun forecast(
        transactions: List<ExpenseTransaction>,
        today: LocalDate,
        recurring: List<RecurringDetector.RecurringPayment> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
        random: Random = Random.Default,
    ): Forecast {
        val month = YearMonth.from(today)
        fun dateOf(tx: ExpenseTransaction) = LocalDate.ofInstant(Instant.ofEpochMilli(tx.transactionTime), zone)

        val spent = transactions.filter {
            it.status == TransactionStatus.INCLUDED && it.direction == Direction.EXPENSE
        }
        val currentSpent = spent
            .filter { val d = dateOf(it); YearMonth.from(d) == month && !d.isAfter(today) }
            .sumOf { it.amount }

        if (spent.isEmpty()) return Forecast(false, currentSpent, currentSpent, currentSpent, currentSpent)

        // History span available for bootstrapping (capped at the 60-day pool window).
        val earliestData = spent.minOf { dateOf(it) }
        val poolStart = maxOf(today.minusDays(POOL_DAYS - 1), earliestData)
        val availableDays = ChronoUnit.DAYS.between(poolStart, today) + 1
        if (availableDays < MIN_DAYS) return Forecast(false, currentSpent, 0, 0, 0)

        // Daily totals over the pool, excluding recurring merchants (added back as fixed amounts).
        val recurringKeys = recurring.map { it.merchantKey }.toSet()
        val poolSpent = spent.filter {
            val d = dateOf(it)
            !d.isBefore(poolStart) && !d.isAfter(today) &&
                MerchantNormalizer.normalize(it.merchant) !in recurringKeys
        }
        val byDate = poolSpent.groupBy { dateOf(it) }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val perWeekday = HashMap<DayOfWeek, MutableList<Long>>()
        var d = poolStart
        while (!d.isAfter(today)) {
            perWeekday.getOrPut(d.dayOfWeek) { mutableListOf() }.add(byDate[d] ?: 0L)
            d = d.plusDays(1)
        }

        val remainingDays = ((today.dayOfMonth + 1)..month.lengthOfMonth()).map { month.atDay(it) }
        val fixedRemaining = fixedRecurringThisMonth(recurring, today, month)

        // Each remaining day draws a same-weekday sample; 1000 simulated month totals.
        val totals = LongArray(ITERATIONS)
        for (i in 0 until ITERATIONS) {
            var sim = 0L
            for (day in remainingDays) {
                val pool = perWeekday[day.dayOfWeek]
                if (!pool.isNullOrEmpty()) sim += pool[random.nextInt(pool.size)]
            }
            totals[i] = currentSpent + sim + fixedRemaining
        }
        totals.sort()
        return Forecast(
            hasEnoughData = true,
            currentSpent = currentSpent,
            p10 = percentile(totals, 10),
            p50 = percentile(totals, 50),
            p90 = percentile(totals, 90),
        )
    }

    /** Recurring occurrences still due after today, within this month, as a fixed sum. */
    private fun fixedRecurringThisMonth(
        recurring: List<RecurringDetector.RecurringPayment>,
        today: LocalDate,
        month: YearMonth,
    ): Long {
        val end = month.atEndOfMonth()
        var total = 0L
        for (p in recurring) {
            var occ = p.nextDate
            while (!occ.isAfter(end)) {
                if (occ.isAfter(today)) total += p.amount
                occ = if (p.cycle == RecurringDetector.Cycle.WEEKLY) occ.plusDays(7) else occ.plusMonths(1)
            }
        }
        return total
    }

    /** Nearest-rank percentile over a pre-sorted array. */
    private fun percentile(sorted: LongArray, p: Int): Long {
        if (sorted.isEmpty()) return 0
        val idx = Math.round((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}
