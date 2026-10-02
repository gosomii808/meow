package com.myfamily.meow.analysis

import com.myfamily.meow.data.entity.ClassificationSource
import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus

/**
 * Classification accuracy, measured as the user-correction rate (spec B / "측정: 사용자 수정률").
 *
 * A reviewed row is "corrected" when the user picked a [ExpenseTransaction.finalCategory] that
 * differs from the predicted one. A null finalCategory means the prediction was accepted as-is,
 * so it is not a correction. We report an overall rate plus a breakdown per
 * [ClassificationSource], so we can see which step of the chain needs the most fixing.
 *
 * Pure Kotlin so it runs under JUnit with no device. Only reviewed (status != PENDING) EXPENSE
 * rows are counted; PENDING rows aren't decided yet and income isn't categorized.
 */
object AccuracyStats {
    data class SourceAccuracy(
        val source: ClassificationSource,
        val reviewed: Int,
        val corrected: Int,
    ) {
        /** 0.0 when nothing reviewed yet. */
        val correctionRate: Double get() = if (reviewed == 0) 0.0 else corrected.toDouble() / reviewed
        val correctionPercent: Int get() = Math.round(correctionRate * 100).toInt()
        val accuracyPercent: Int get() = 100 - correctionPercent
    }

    data class Report(
        val reviewed: Int,
        val corrected: Int,
        /** Sources that actually produced at least one reviewed row, busiest first. */
        val bySource: List<SourceAccuracy>,
    ) {
        val correctionRate: Double get() = if (reviewed == 0) 0.0 else corrected.toDouble() / reviewed
        val correctionPercent: Int get() = Math.round(correctionRate * 100).toInt()
        val accuracyPercent: Int get() = 100 - correctionPercent
        val hasData: Boolean get() = reviewed > 0
    }

    private fun isCorrected(tx: ExpenseTransaction): Boolean =
        tx.finalCategory != null && tx.finalCategory != tx.predictedCategory

    fun compute(transactions: List<ExpenseTransaction>): Report {
        val reviewed = transactions.filter {
            it.status != TransactionStatus.PENDING && it.direction == Direction.EXPENSE
        }
        val bySource = reviewed
            .groupBy { it.classificationSource }
            .map { (source, rows) -> SourceAccuracy(source, rows.size, rows.count(::isCorrected)) }
            .sortedByDescending { it.reviewed }
        return Report(
            reviewed = reviewed.size,
            corrected = reviewed.count(::isCorrected),
            bySource = bySource,
        )
    }
}
