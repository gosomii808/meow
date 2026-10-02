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
import java.time.LocalDateTime
import java.time.ZoneId

class SpendingTendencyTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 30)

    private fun tx(amount: Long, at: LocalDateTime, category: Category) = ExpenseTransaction(
        amount = amount,
        merchant = "가게",
        transactionTime = at.atZone(zone).toInstant().toEpochMilli(),
        sourceLabel = "토스",
        predictedCategory = category,
        status = TransactionStatus.INCLUDED,
        source = TransactionSource.NOTIFICATION,
    )

    /** Spread across the window so there's ≥14 days of history. */
    private fun spread(count: Int, amount: Long, category: Category, hour: Int = 13) =
        (0 until count).map { tx(amount, today.minusDays(it.toLong()).atTime(hour, 0), category) }

    @Test fun underTwoWeeksIsBalanced() {
        val rows = (0..4).map { tx(5_000, today.minusDays(it.toLong()).atTime(9, 0), Category.CAFE) }
        val r = SpendingTendency.analyze(rows, today, zone)
        assertFalse(r.hasEnoughData)
        assertEquals(SpendingTendency.Tendency.BALANCED, r.tendency)
    }

    @Test fun cafeHeavyIsCafeType() {
        val rows = spread(20, 5_000, Category.CAFE, hour = 9)
        val r = SpendingTendency.analyze(rows, today, zone)
        assertTrue(r.hasEnoughData)
        assertEquals(SpendingTendency.Tendency.CAFE, r.tendency)
        assertEquals("커피", r.tendency.prop)
    }

    @Test fun lateNightFoodIsDeliveryNight() {
        val rows = spread(20, 18_000, Category.FOOD, hour = 23)
        val r = SpendingTendency.analyze(rows, today, zone)
        assertEquals(SpendingTendency.Tendency.DELIVERY_NIGHT, r.tendency)
    }

    @Test fun shoppingHeavyIsShoppingType() {
        val rows = spread(16, 60_000, Category.SHOPPING, hour = 15)
        val r = SpendingTendency.analyze(rows, today, zone)
        assertEquals(SpendingTendency.Tendency.SHOPPING, r.tendency)
    }

    @Test fun emptyIsBalancedNoData() {
        val r = SpendingTendency.analyze(emptyList(), today, zone)
        assertFalse(r.hasEnoughData)
        assertEquals(SpendingTendency.Tendency.BALANCED, r.tendency)
    }
}
