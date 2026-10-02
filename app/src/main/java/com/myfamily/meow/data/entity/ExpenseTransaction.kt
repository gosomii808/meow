package com.myfamily.meow.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myfamily.meow.classification.Category

/**
 * A spending candidate (PENDING) or a reviewed record. Only INCLUDED rows count toward
 * daily totals, the calendar and statistics.
 */
@Entity(
    tableName = "transactions",
    indices = [Index("status"), Index("transactionTime")],
)
data class ExpenseTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawEventId: Long? = null,
    val amount: Long,
    val merchant: String,
    val transactionTime: Long,
    /** Display name of the app/card that produced it, e.g. "토스", "KB국민". */
    val sourceLabel: String,
    val predictedCategory: Category = Category.ETC,
    val finalCategory: Category? = null,
    val classificationSource: ClassificationSource = ClassificationSource.RULE,
    val status: TransactionStatus = TransactionStatus.PENDING,
    val source: TransactionSource,
    val duplicateGroupId: String? = null,
    val memo: String? = null,
    val confirmedAt: Long? = null,
) {
    val category: Category get() = finalCategory ?: predictedCategory
}

enum class TransactionStatus { PENDING, INCLUDED, EXCLUDED }

enum class TransactionSource { NOTIFICATION, MANUAL }

enum class ClassificationSource { RULE, APP, LOCATION, AI, USER }
