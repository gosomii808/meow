package com.myfamily.meow.analysis

import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Finds likely recurring (subscription-style) payments (spec C / §2-1). Display only — it never
 * changes a transaction's category. Pure Kotlin so it runs under JUnit.
 *
 * Rules: group INCLUDED expenses by normalized merchant ([MerchantNormalizer]); need at least
 * [MIN_COUNT] payments; every amount within ±10% of the group's median; and the median gap
 * between consecutive payments is weekly (7±2 days) or monthly (28–33 days).
 */
object RecurringDetector {
    private const val MIN_COUNT = 3
    private const val AMOUNT_TOLERANCE = 0.10

    enum class Cycle { WEEKLY, MONTHLY }

    data class RecurringPayment(
        val merchant: String,
        val merchantKey: String,
        val amount: Long,
        val cycle: Cycle,
        val lastDate: LocalDate,
        val nextDate: LocalDate,
        /** Normalized to a monthly figure, so weekly and monthly items can be summed. */
        val monthlyAmount: Long,
        val count: Int,
    )

    fun detect(
        transactions: List<ExpenseTransaction>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<RecurringPayment> {
        val spent = transactions.filter {
            it.status == TransactionStatus.INCLUDED && it.direction == Direction.EXPENSE
        }
        return spent
            .groupBy { MerchantNormalizer.normalize(it.merchant) }
            .mapNotNull { (key, rows) -> analyzeGroup(key, rows, zone) }
            .sortedByDescending { it.monthlyAmount }
    }

    private fun analyzeGroup(
        key: String,
        rows: List<ExpenseTransaction>,
        zone: ZoneId,
    ): RecurringPayment? {
        if (rows.size < MIN_COUNT) return null

        val amounts = rows.map { it.amount }.sorted()
        val medianAmount = median(amounts)
        if (medianAmount <= 0) return null
        val withinTolerance = amounts.all { abs(it - medianAmount) <= medianAmount * AMOUNT_TOLERANCE }
        if (!withinTolerance) return null

        val dates = rows.map { LocalDate.ofInstant(Instant.ofEpochMilli(it.transactionTime), zone) }.sorted()
        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.filter { it > 0 }
        if (gaps.size < MIN_COUNT - 1) return null
        val medianGap = median(gaps)
        val cycle = when (medianGap) {
            in 5..9 -> Cycle.WEEKLY
            in 28..33 -> Cycle.MONTHLY
            else -> return null
        }

        val last = dates.last()
        val next = last.plusDays(medianGap)
        val monthly = when (cycle) {
            // 52 weeks a year spread over 12 months.
            Cycle.WEEKLY -> Math.round(medianAmount * 52.0 / 12.0)
            Cycle.MONTHLY -> medianAmount
        }
        return RecurringPayment(
            merchant = displayName(rows),
            merchantKey = key,
            amount = medianAmount,
            cycle = cycle,
            lastDate = last,
            nextDate = next,
            monthlyAmount = monthly,
            count = rows.size,
        )
    }

    /** The most frequent raw merchant name, so the card shows "스타벅스" not "스타벅스 강남점". */
    private fun displayName(rows: List<ExpenseTransaction>): String =
        rows.groupingBy { it.merchant }.eachCount().maxByOrNull { it.value }?.key ?: rows.first().merchant

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }
}
