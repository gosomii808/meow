package com.myfamily.meow.analysis

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Flags categories whose last 7 days are unusually high versus their own recent history
 * (spec D / §2-2). Robust (median + MAD) so one big week doesn't poison the baseline. Display
 * only. Pure Kotlin for JUnit.
 *
 * Per category: "window 0" = the 7 days ending today; baseline = the previous [BASELINE_WINDOWS]
 * 7-day windows. Needs at least [MIN_BASELINE] baseline windows. An anomaly is
 * robust z ≥ [Z_THRESHOLD] (or, when MAD is 0, current ≥ 2× median), and the raw gap from the
 * median must be at least [MIN_GAP] won so tiny amounts don't trigger it.
 */
object AnomalyDetector {
    private const val BASELINE_WINDOWS = 8
    private const val MIN_BASELINE = 4
    private const val WINDOW_DAYS = 7L
    private const val Z_THRESHOLD = 3.5
    private const val MIN_GAP = 10_000L
    private const val MAD_CONSTANT = 0.6745

    data class Anomaly(
        val category: Category,
        val current: Long,
        val baselineMedian: Long,
        /** current / median; 0 when median is 0. */
        val ratio: Double,
        val z: Double,
    )

    fun detect(
        transactions: List<ExpenseTransaction>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Anomaly> {
        val spent = transactions.filter {
            it.status == TransactionStatus.INCLUDED && it.direction == Direction.EXPENSE
        }
        if (spent.isEmpty()) return emptyList()

        // Window index 0 = current 7 days ending today; 1..BASELINE_WINDOWS = older windows.
        fun windowOf(date: LocalDate): Int {
            val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(date, today)
            if (daysAgo < 0) return -1 // future
            return (daysAgo / WINDOW_DAYS).toInt()
        }

        // How many whole 7-day baseline windows of history we actually have (windows with no
        // data still count as 0 spending, but windows from before we started collecting don't
        // exist). Based on the oldest transaction across all categories.
        val earliest = spent.minOf { LocalDate.ofInstant(Instant.ofEpochMilli(it.transactionTime), zone) }
        val validBaselineCount = windowOf(earliest).coerceAtMost(BASELINE_WINDOWS)
        if (validBaselineCount < MIN_BASELINE) return emptyList()

        val byCategory = spent.groupBy { it.category }
        val result = mutableListOf<Anomaly>()
        for ((category, rows) in byCategory) {
            val sums = LongArray(BASELINE_WINDOWS + 1)
            for (tx in rows) {
                val date = LocalDate.ofInstant(Instant.ofEpochMilli(tx.transactionTime), zone)
                val w = windowOf(date)
                if (w in 0..BASELINE_WINDOWS) sums[w] += tx.amount
            }
            val current = sums[0]
            val baseline = (1..validBaselineCount).map { sums[it] }

            val median = median(baseline)
            val gap = current - median
            if (gap < MIN_GAP) continue

            val mad = median(baseline.map { abs(it - median) })
            val z: Double
            val isAnomaly: Boolean
            if (mad == 0L) {
                z = Double.POSITIVE_INFINITY
                isAnomaly = median > 0 && current >= median * 2
            } else {
                z = MAD_CONSTANT * (current - median) / mad
                isAnomaly = z >= Z_THRESHOLD
            }
            if (isAnomaly) {
                result += Anomaly(
                    category = category,
                    current = current,
                    baselineMedian = median,
                    ratio = if (median > 0) current.toDouble() / median else 0.0,
                    z = z,
                )
            }
        }
        return result.sortedByDescending { it.current - it.baselineMedian }
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }
}
