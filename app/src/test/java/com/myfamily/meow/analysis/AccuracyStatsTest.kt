package com.myfamily.meow.analysis

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ClassificationSource
import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AccuracyStatsTest {
    private fun tx(
        predicted: Category,
        final: Category? = null,
        source: ClassificationSource = ClassificationSource.RULE,
        status: TransactionStatus = TransactionStatus.INCLUDED,
        direction: Direction = Direction.EXPENSE,
    ) = ExpenseTransaction(
        amount = 1_000,
        merchant = "가게",
        transactionTime = 0,
        sourceLabel = "토스",
        predictedCategory = predicted,
        finalCategory = final,
        classificationSource = source,
        status = status,
        source = TransactionSource.NOTIFICATION,
        direction = direction,
    )

    @Test fun correctedIsFinalDifferingFromPredicted() {
        val rows = listOf(
            tx(Category.CAFE),                                 // accepted (final null) → not corrected
            tx(Category.CAFE, final = Category.CAFE),          // chose the same → not corrected
            tx(Category.CAFE, final = Category.FOOD),          // corrected
        )
        val r = AccuracyStats.compute(rows)
        assertEquals(3, r.reviewed)
        assertEquals(1, r.corrected)
        assertEquals(33, r.correctionPercent)
        assertEquals(67, r.accuracyPercent)
    }

    @Test fun ignoresPendingIncomeRows() {
        val rows = listOf(
            tx(Category.CAFE, final = Category.FOOD, status = TransactionStatus.PENDING), // not reviewed
            tx(Category.CAFE, final = Category.FOOD, direction = Direction.INCOME),        // income excluded
            tx(Category.CAFE, final = Category.FOOD),                                      // counted
        )
        val r = AccuracyStats.compute(rows)
        assertEquals(1, r.reviewed)
        assertEquals(1, r.corrected)
    }

    @Test fun breaksDownBySourceBusiestFirst() {
        val rows = listOf(
            tx(Category.CAFE, source = ClassificationSource.RULE),
            tx(Category.CAFE, final = Category.FOOD, source = ClassificationSource.RULE),
            tx(Category.ETC, final = Category.MEDICAL, source = ClassificationSource.AI),
            tx(Category.ETC, source = ClassificationSource.AI),
            tx(Category.LIVING, source = ClassificationSource.APP),
        )
        val r = AccuracyStats.compute(rows)
        // RULE and AI have 2 each (tie), APP has 1 and must come last.
        assertEquals(ClassificationSource.APP, r.bySource.last().source)
        val rule = r.bySource.first { it.source == ClassificationSource.RULE }
        assertEquals(2, rule.reviewed)
        assertEquals(1, rule.corrected)
        assertEquals(50, rule.correctionPercent)
        val ai = r.bySource.first { it.source == ClassificationSource.AI }
        assertEquals(1, ai.corrected)
    }

    @Test fun emptyInputIsZeroNotCrash() {
        val r = AccuracyStats.compute(emptyList())
        assertEquals(0, r.reviewed)
        assertEquals(0, r.correctionPercent)
        assertEquals(false, r.hasData)
        assertEquals(emptyList<AccuracyStats.SourceAccuracy>(), r.bySource)
    }
}
