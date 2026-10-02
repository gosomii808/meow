package com.myfamily.meow.analysis

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecurringDetectorTest {
    private val zone = ZoneId.of("Asia/Seoul")

    private fun tx(
        merchant: String,
        amount: Long,
        date: LocalDate,
        status: TransactionStatus = TransactionStatus.INCLUDED,
    ) = ExpenseTransaction(
        amount = amount,
        merchant = merchant,
        transactionTime = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        sourceLabel = "토스",
        predictedCategory = Category.SUBSCRIPTION,
        status = status,
        source = TransactionSource.NOTIFICATION,
    )

    @Test fun detectsMonthlySubscription() {
        val rows = listOf(
            tx("넷플릭스", 13_500, LocalDate.of(2026, 7, 5)),
            tx("넷플릭스", 13_500, LocalDate.of(2026, 8, 5)),
            tx("넷플릭스", 13_500, LocalDate.of(2026, 9, 4)),
        )
        val r = RecurringDetector.detect(rows, zone)
        assertEquals(1, r.size)
        val p = r.first()
        assertEquals(RecurringDetector.Cycle.MONTHLY, p.cycle)
        assertEquals(13_500, p.amount)
        assertEquals(13_500, p.monthlyAmount)
        assertEquals(LocalDate.of(2026, 9, 4), p.lastDate)
        assertEquals(3, p.count)
    }

    @Test fun detectsWeeklyAndConvertsToMonthly() {
        val rows = (0..3).map { tx("요가학원", 20_000, LocalDate.of(2026, 9, 1).plusDays(it * 7L)) }
        val r = RecurringDetector.detect(rows, zone)
        assertEquals(RecurringDetector.Cycle.WEEKLY, r.first().cycle)
        // 20,000 * 52 / 12 ≈ 86,667
        assertEquals(86_667, r.first().monthlyAmount)
    }

    @Test fun groupsBranchesViaNormalizedName() {
        val rows = listOf(
            tx("스타벅스 강남점", 5_000, LocalDate.of(2026, 7, 10)),
            tx("스타벅스 역삼점", 5_200, LocalDate.of(2026, 8, 9)),
            tx("스타벅스 서초점", 4_900, LocalDate.of(2026, 9, 8)),
        )
        val r = RecurringDetector.detect(rows, zone)
        assertEquals(1, r.size)
        assertEquals(3, r.first().count)
    }

    @Test fun rejectsVaryingAmounts() {
        val rows = listOf(
            tx("마트", 10_000, LocalDate.of(2026, 7, 5)),
            tx("마트", 30_000, LocalDate.of(2026, 8, 5)),
            tx("마트", 50_000, LocalDate.of(2026, 9, 4)),
        )
        assertTrue(RecurringDetector.detect(rows, zone).isEmpty())
    }

    @Test fun rejectsIntervalOutsideWeeklyOrMonthly() {
        // Median gap ~2 days (daily-ish) is neither weekly nor monthly.
        val rows = listOf(
            tx("가게", 10_000, LocalDate.of(2026, 9, 1)),
            tx("가게", 10_000, LocalDate.of(2026, 9, 3)),
            tx("가게", 10_000, LocalDate.of(2026, 9, 6)),
        )
        assertTrue(RecurringDetector.detect(rows, zone).isEmpty())
    }

    @Test fun needsAtLeastThree() {
        val rows = listOf(
            tx("넷플릭스", 13_500, LocalDate.of(2026, 8, 5)),
            tx("넷플릭스", 13_500, LocalDate.of(2026, 9, 5)),
        )
        assertTrue(RecurringDetector.detect(rows, zone).isEmpty())
    }

    @Test fun ignoresNonIncludedRows() {
        val rows = listOf(
            tx("넷플릭스", 13_500, LocalDate.of(2026, 7, 5), TransactionStatus.EXCLUDED),
            tx("넷플릭스", 13_500, LocalDate.of(2026, 8, 5)),
            tx("넷플릭스", 13_500, LocalDate.of(2026, 9, 4)),
            tx("넷플릭스", 13_500, LocalDate.of(2026, 10, 4), TransactionStatus.PENDING),
        )
        // Only 2 INCLUDED rows remain → not recurring.
        assertTrue(RecurringDetector.detect(rows, zone).isEmpty())
    }
}
