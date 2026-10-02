package com.myfamily.meow.ai

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SpendingDigestTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 10)

    private fun tx(
        merchant: String,
        amount: Long,
        at: LocalDateTime,
        category: Category,
        status: TransactionStatus = TransactionStatus.INCLUDED,
    ) = ExpenseTransaction(
        amount = amount,
        merchant = merchant,
        transactionTime = at.atZone(zone).toInstant().toEpochMilli(),
        sourceLabel = "토스",
        predictedCategory = category,
        status = status,
        source = TransactionSource.NOTIFICATION,
    )

    private val rows = listOf(
        tx("스타벅스 강남점", 5_500, LocalDateTime.of(2026, 10, 2, 8, 30), Category.CAFE),
        tx("스타벅스 역삼점", 6_000, LocalDateTime.of(2026, 10, 9, 15, 0), Category.CAFE),
        tx("배달의민족", 20_000, LocalDateTime.of(2026, 10, 4, 20, 0), Category.FOOD),
        tx("무신사", 50_000, LocalDateTime.of(2026, 10, 5, 23, 0), Category.SHOPPING, TransactionStatus.EXCLUDED),
        tx("CGV", 15_000, LocalDateTime.of(2026, 9, 6, 19, 0), Category.CULTURE),
        tx("미래 결제", 99_000, LocalDateTime.of(2026, 10, 20, 12, 0), Category.ETC),
    )

    @Test
    fun totalsOnlyCountIncludedSpendingUpToToday() {
        val d = SpendingDigest.build(rows, today, zone = zone)
        assertTrue(d, d.contains("총 31,500원, 3건, 하루 평균 3,150원"))
        assertFalse("excluded rows must not count", d.contains("무신사"))
        assertFalse("future rows must not count", d.contains("미래 결제"))
    }

    @Test
    fun categoriesAndPlacesArePrecomputed() {
        val d = SpendingDigest.build(rows, today, zone = zone)
        assertTrue(d, d.contains("식비 20,000원 (1건, 63%)"))
        assertTrue(d, d.contains("카페 11,500원 (2건, 36%)"))
        assertTrue(d, d.contains("[지난달 9월] 총 15,000원"))
        // Excluded 무신사 doesn't count, so 쇼핑 is listed as having no spending.
        assertTrue(d, Regex("""기록 없는 카테고리 \(0원\)\] .*쇼핑""").containsMatchIn(d))
    }

    @Test
    fun goalPaceIsSpelledOut() {
        // 310,000 goal over 31 days → pace 100,000 by day 10; 31,500 spent.
        val d = SpendingDigest.build(rows, today, monthlyGoal = 310_000, zone = zone)
        assertTrue(d, d.contains("남은 금액 278,500원"))
        assertTrue(d, d.contains("68,500원 여유 있음"))
    }
}
