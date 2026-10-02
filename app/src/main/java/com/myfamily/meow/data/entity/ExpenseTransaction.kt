package com.myfamily.meow.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myfamily.meow.classification.Category

/**
 * A money-movement candidate (PENDING) or a reviewed record. Only INCLUDED rows count toward
 * totals; spending statistics use [Direction.EXPENSE] only, income is shown separately.
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
    @ColumnInfo(defaultValue = "EXPENSE") val direction: Direction = Direction.EXPENSE,
    /** Same amount within a few minutes as another notification (spec §9). */
    val duplicateGroupId: String? = null,
    /** Text suggests charge/transfer/withdrawal rather than a purchase (spec §9). */
    @ColumnInfo(defaultValue = "0") val transferLikely: Boolean = false,
    /** On-device AI already tried to classify this row (success or not). */
    @ColumnInfo(defaultValue = "0") val aiTried: Boolean = false,
    val memo: String? = null,
    /** Split bill (up-swipe 정산): [amount] is my share, [originalAmount] what was actually paid. */
    val splitCount: Int? = null,
    val originalAmount: Long? = null,
    val confirmedAt: Long? = null,
) {
    val category: Category get() = finalCategory ?: predictedCategory
}

enum class TransactionStatus { PENDING, INCLUDED, EXCLUDED }

enum class Direction { EXPENSE, INCOME }

enum class TransactionSource { NOTIFICATION, MANUAL }

enum class ClassificationSource { RULE, APP, LOCATION, AI, USER }
