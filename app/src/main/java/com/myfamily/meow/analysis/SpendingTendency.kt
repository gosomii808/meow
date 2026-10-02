package com.myfamily.meow.analysis

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

/**
 * Picks the cat's spending-personality archetype (spec J / §2-4). With a single user we can't
 * cluster, so we compare a 30-day feature vector to a few hand-defined archetype centroids and
 * take the nearest (cosine). Each archetype maps to a character prop from spec §18. Under
 * [MIN_DAYS] of history we default to [Tendency.BALANCED]. Pure Kotlin for JUnit.
 *
 * Feature vector = per-category spending shares (11) + late-night(22–4시) amount share + small
 * (≤10,000원) count share.
 */
object SpendingTendency {
    private const val WINDOW_DAYS = 30L
    private const val MIN_DAYS = 14L
    private const val SMALL_MAX = 10_000L
    private val CATS = Category.entries
    private val DIM = CATS.size + 2
    private val LATE_IDX = CATS.size
    private val SMALL_IDX = CATS.size + 1

    enum class Tendency(val label: String, val prop: String, val emoji: String) {
        CAFE("카페형", "커피", "☕"),
        DELIVERY_NIGHT("배달·야식형", "배달 봉투", "🍜"),
        SHOPPING("쇼핑형", "쇼핑백", "🛍️"),
        LIFE_TRANSPORT("생활·교통형", "장바구니", "🏠"),
        BALANCED("균형형", "저금통", "⚖️"),
    }

    data class Result(val tendency: Tendency, val hasEnoughData: Boolean)

    // Centroids over the same layout. They need not be normalized; cosine ignores magnitude.
    private val centroids: Map<Tendency, FloatArray> by lazy {
        fun vec(build: FloatArray.() -> Unit) = FloatArray(DIM).apply(build)
        mapOf(
            Tendency.CAFE to vec { this[Category.CAFE.ordinal] = 0.6f; this[LATE_IDX] = 0.1f; this[SMALL_IDX] = 0.6f },
            Tendency.DELIVERY_NIGHT to vec { this[Category.FOOD.ordinal] = 0.6f; this[LATE_IDX] = 0.6f; this[SMALL_IDX] = 0.2f },
            Tendency.SHOPPING to vec { this[Category.SHOPPING.ordinal] = 0.6f; this[LATE_IDX] = 0.2f; this[SMALL_IDX] = 0.1f },
            Tendency.LIFE_TRANSPORT to vec { this[Category.LIVING.ordinal] = 0.35f; this[Category.TRANSPORT.ordinal] = 0.35f; this[LATE_IDX] = 0.1f; this[SMALL_IDX] = 0.4f },
            Tendency.BALANCED to vec { CATS.forEach { this[it.ordinal] = 1f / CATS.size }; this[LATE_IDX] = 0.2f; this[SMALL_IDX] = 0.4f },
        )
    }

    fun analyze(
        transactions: List<ExpenseTransaction>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result {
        fun at(tx: ExpenseTransaction) = LocalDateTime.ofInstant(Instant.ofEpochMilli(tx.transactionTime), zone)
        val windowStart = today.minusDays(WINDOW_DAYS - 1)
        val spent = transactions.filter {
            it.status == TransactionStatus.INCLUDED && it.direction == Direction.EXPENSE &&
                at(it).toLocalDate().let { d -> !d.isBefore(windowStart) && !d.isAfter(today) }
        }
        if (spent.isEmpty()) return Result(Tendency.BALANCED, false)
        val earliest = spent.minOf { at(it).toLocalDate() }
        if (ChronoUnit.DAYS.between(earliest, today) + 1 < MIN_DAYS) return Result(Tendency.BALANCED, false)

        val features = featureVector(spent, ::at)
        val best = centroids.maxByOrNull { cosine(features, it.value) }!!.key
        return Result(best, true)
    }

    private fun featureVector(spent: List<ExpenseTransaction>, at: (ExpenseTransaction) -> LocalDateTime): FloatArray {
        val v = FloatArray(DIM)
        val total = spent.sumOf { it.amount }.coerceAtLeast(1)
        for (c in CATS) {
            v[c.ordinal] = spent.filter { it.category == c }.sumOf { it.amount }.toFloat() / total
        }
        v[LATE_IDX] = spent.filter { val h = at(it).hour; h >= 22 || h < 4 }.sumOf { it.amount }.toFloat() / total
        v[SMALL_IDX] = spent.count { it.amount in 1..SMALL_MAX }.toFloat() / spent.size
        return v
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom == 0f) 0f else dot / denom
    }
}
