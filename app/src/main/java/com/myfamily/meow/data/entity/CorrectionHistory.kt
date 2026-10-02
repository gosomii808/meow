package com.myfamily.meow.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myfamily.meow.classification.Category

/** A category the user chose for a merchant; looked up first for later payments (spec §11). */
@Entity(tableName = "correction_history", indices = [Index("merchantKey")])
data class CorrectionHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    /** See [com.myfamily.meow.classification.merchantKey]. */
    val merchantKey: String,
    val foregroundApp: String? = null,
    val predictedCategory: Category,
    val correctedCategory: Category,
    val createdAt: Long,
)
