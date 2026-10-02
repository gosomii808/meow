package com.myfamily.meow.analysis

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AnomalyDetectorTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 10)

    private fun tx(amount: Long, date: LocalDate, category: Category) = ExpenseTransaction(
        amount = amount,
        merchant = "가게",
        transactionTime = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        sourceLabel = "토스",
        predictedCategory = category,
        status = TransactionStatus.INCLUDED,
        source = TransactionSource.NOTIFICATION,
    )

    /** One payment per 7-day window: window 0 = today, window k = k*7 days before. */
    private fun weekly(category: Category, amountsNewestFirst: List<Long>): List<ExpenseTransaction> =
        amountsNewestFirst.mapIndexed { i, amt -> tx(amt, today.minusDays(i * 7L + 1), category) }

    @Test fun flagsSpikeAgainstStableBaseline() {
        // current 50,000 vs baseline ~20,000 (8 windows).
        val rows = weekly(Category.FOOD, listOf(50_000L) + List(8) { 20_000L })
        val a = AnomalyDetector.detect(rows, today, zone)
        assertEquals(1, a.size)
        assertEquals(Category.FOOD, a.first().category)
        assertEquals(50_000, a.first().current)
        assertEquals(20_000, a.first().baselineMedian)
        assertTrue(a.first().ratio > 2.0)
    }

    @Test fun ignoresSmallAbsoluteGap() {
        // current only 5,000 above median → below MIN_GAP even if relatively large.
        val rows = weekly(Category.CAFE, listOf(8_000L) + List(8) { 3_000L })
        assertTrue(AnomalyDetector.detect(rows, today, zone).isEmpty())
    }

    @Test fun needsFourBaselineWindows() {
        // current + only 3 baseline windows.
        val rows = weekly(Category.FOOD, listOf(90_000L, 10_000L, 10_000L, 10_000L))
        assertTrue(AnomalyDetector.detect(rows, today, zone).isEmpty())
    }

    @Test fun madZeroFallsBackToDoubleRule() {
        // Baseline all identical → MAD 0. current must be ≥ 2× median to flag.
        val flagged = weekly(Category.FOOD, listOf(40_000L) + List(8) { 15_000L })
        assertTrue(AnomalyDetector.detect(flagged, today, zone).isNotEmpty())
        // 25,000 is < 2× 15,000 (=30,000) → not flagged, though gap ≥ 10,000.
        val notFlagged = weekly(Category.FOOD, listOf(25_000L) + List(8) { 15_000L })
        assertTrue(AnomalyDetector.detect(notFlagged, today, zone).isEmpty())
    }

    @Test fun stableSpendingIsNotAnomalous() {
        val rows = weekly(Category.TRANSPORT, List(9) { 30_000L })
        assertTrue(AnomalyDetector.detect(rows, today, zone).isEmpty())
    }
}
