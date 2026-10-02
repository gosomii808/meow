package com.myfamily.meow.analysis

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class InsightEngineTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 15)
    private var nextId = 1L

    private fun tx(
        amount: Long,
        at: LocalDateTime,
        category: Category = Category.FOOD,
        merchant: String = "가게",
    ) = ExpenseTransaction(
        id = nextId++,
        amount = amount,
        merchant = merchant,
        transactionTime = at.atZone(zone).toInstant().toEpochMilli(),
        sourceLabel = "토스",
        predictedCategory = category,
        status = TransactionStatus.INCLUDED,
        source = TransactionSource.NOTIFICATION,
    )

    @Test fun categoryIncreaseNeedsThreeRecentTx() {
        // Two big café payments in the last 7 days → below MIN_TX, no CATEGORY_UP.
        val rows = listOf(
            tx(20_000, today.atTime(9, 0), Category.CAFE),
            tx(20_000, today.minusDays(1).atTime(9, 0), Category.CAFE),
        )
        val insights = InsightEngine.generate(rows, today, zone = zone)
        assertTrue(insights.none { it.type == InsightEngine.InsightType.CATEGORY_UP })
    }

    @Test fun detectsCategoryIncreaseWithFormattedFigures() {
        val rows = buildList {
            // 3 café payments this week.
            repeat(3) { add(tx(10_000, today.minusDays(it.toLong()).atTime(9, 0), Category.CAFE)) }
            // Small café history in the prior 4 weeks → weekly avg much lower.
            add(tx(5_000, today.minusDays(20).atTime(9, 0), Category.CAFE))
        }
        val insights = InsightEngine.generate(rows, today, zone = zone)
        val up = insights.first { it.type == InsightEngine.InsightType.CATEGORY_UP }
        assertEquals(Category.CAFE, up.category)
        assertTrue("has won figure", up.figures.any { it.endsWith("원") })
        assertTrue("has multiple figure", up.figures.any { it.endsWith("배") })
        assertTrue("has count figure", up.figures.any { it.endsWith("번") })
    }

    @Test fun returnsAtMostThree() {
        // Lots of varied spending to generate many candidates.
        val rows = buildList {
            repeat(5) { add(tx(30_000, today.minusDays(it.toLong()).atTime(23, 30), Category.FOOD)) } // late night
            repeat(4) { add(tx(40_000, today.minusDays(it.toLong()).atTime(2, 0), Category.SHOPPING)) }
            add(tx(500_000, today.minusDays(1).atTime(14, 0), Category.TRAVEL, "호텔"))
        }
        val insights = InsightEngine.generate(rows, today, zone = zone)
        assertTrue(insights.size <= 3)
    }

    @Test fun collapsesSameCategoryToHighestScore() {
        // Café both spikes this week AND is flagged as an anomaly → one café insight only.
        val rows = buildList {
            repeat(3) { add(tx(50_000, today.minusDays(it.toLong()).atTime(9, 0), Category.CAFE)) }
            add(tx(3_000, today.minusDays(20).atTime(9, 0), Category.CAFE))
        }
        val anomaly = AnomalyDetector.Anomaly(Category.CAFE, 150_000, 20_000, 7.5, 9.0)
        val insights = InsightEngine.generate(rows, today, anomalies = listOf(anomaly), zone = zone)
        assertEquals(1, insights.count { it.category == Category.CAFE })
    }

    @Test fun forecastOverGoalProducesInsight() {
        val rows = (0..9).map { tx(10_000, today.minusDays(it.toLong()).atTime(12, 0)) }
        val forecast = MonthForecaster.Forecast(hasEnoughData = true, currentSpent = 300_000, p10 = 550_000, p50 = 700_000, p90 = 850_000)
        val insights = InsightEngine.generate(rows, today, monthlyGoal = 600_000, forecast = forecast, zone = zone)
        assertTrue(insights.any { it.type == InsightEngine.InsightType.FORECAST_OVER })
    }

    @Test fun emptyInputReturnsNothing() {
        assertTrue(InsightEngine.generate(emptyList(), today, zone = zone).isEmpty())
    }
}
