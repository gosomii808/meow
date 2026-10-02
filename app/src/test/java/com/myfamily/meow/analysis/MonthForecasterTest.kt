package com.myfamily.meow.analysis

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class MonthForecasterTest {
    private val zone = ZoneId.of("Asia/Seoul")

    private fun tx(amount: Long, date: LocalDate, merchant: String = "가게") = ExpenseTransaction(
        amount = amount,
        merchant = merchant,
        transactionTime = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        sourceLabel = "토스",
        predictedCategory = Category.FOOD,
        status = TransactionStatus.INCLUDED,
        source = TransactionSource.NOTIFICATION,
    )

    @Test fun notEnoughDataUnderTwoWeeks() {
        val today = LocalDate.of(2026, 10, 10)
        val rows = (0..5).map { tx(10_000, today.minusDays(it.toLong())) } // only ~6 days
        val f = MonthForecaster.forecast(rows, today, zone = zone, random = Random(1))
        assertFalse(f.hasEnoughData)
    }

    @Test fun constantSpendingGivesTightRangeAroundExpected() {
        val today = LocalDate.of(2026, 10, 15)
        // 10,000 every day for the last 40 days.
        val rows = (0..39).map { tx(10_000, today.minusDays(it.toLong())) }
        val f = MonthForecaster.forecast(rows, today, zone = zone, random = Random(42))
        assertTrue(f.hasEnoughData)
        // Spent so far this month: days 1..15 = 150,000. Remaining 16 days × 10,000 = 160,000.
        assertEquals(150_000, f.currentSpent)
        assertEquals(310_000, f.p10)
        assertEquals(310_000, f.p50)
        assertEquals(310_000, f.p90)
    }

    @Test fun percentilesAreOrdered() {
        val today = LocalDate.of(2026, 10, 15)
        // Vary amounts within each weekday (not just across weekdays) so the bootstrap spreads.
        val rows = (0..41).map {
            val day = today.minusDays(it.toLong())
            val amt = 5_000L + (it * 7919L % 45_000L) // same-weekday days differ week to week
            tx(amt, day)
        }
        val f = MonthForecaster.forecast(rows, today, zone = zone, random = Random(7))
        assertTrue(f.hasEnoughData)
        assertTrue("p10 <= p50", f.p10 <= f.p50)
        assertTrue("p50 <= p90", f.p50 <= f.p90)
        assertTrue("spread exists", f.p90 > f.p10)
    }

    @Test fun reproducibleWithSameSeed() {
        val today = LocalDate.of(2026, 10, 15)
        val rows = (0..41).map {
            val day = today.minusDays(it.toLong())
            tx(if (day.dayOfWeek.value >= 6) 40_000L else 6_000L, day)
        }
        val a = MonthForecaster.forecast(rows, today, zone = zone, random = Random(123))
        val b = MonthForecaster.forecast(rows, today, zone = zone, random = Random(123))
        assertEquals(a.p50, b.p50)
        assertEquals(a.p90, b.p90)
    }

    @Test fun recurringAddedAsFixedNotDoubleCounted() {
        val today = LocalDate.of(2026, 10, 10)
        // 40 days of 1,000/day general spending so there IS enough history.
        val general = (0..39).map { tx(1_000, today.minusDays(it.toLong()), merchant = "분식") }
        // A monthly subscription due on the 25th (after today), amount 13,500.
        val sub = RecurringDetector.RecurringPayment(
            merchant = "넷플릭스",
            merchantKey = "넷플릭스",
            amount = 13_500,
            cycle = RecurringDetector.Cycle.MONTHLY,
            lastDate = LocalDate.of(2026, 9, 25),
            nextDate = LocalDate.of(2026, 10, 25),
            monthlyAmount = 13_500,
            count = 3,
        )
        val f = MonthForecaster.forecast(general, today, recurring = listOf(sub), zone = zone, random = Random(1))
        assertTrue(f.hasEnoughData)
        // The 13,500 subscription must be included in the forecast total.
        val withoutSub = MonthForecaster.forecast(general, today, zone = zone, random = Random(1))
        assertEquals(withoutSub.p50 + 13_500, f.p50)
    }
}
