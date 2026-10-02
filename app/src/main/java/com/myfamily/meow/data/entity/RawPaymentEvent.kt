package com.myfamily.meow.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Original payment notification. Never deleted, even when the user excludes the candidate. */
@Entity(
    tableName = "raw_payment_events",
    indices = [Index(value = ["fingerprint"], unique = true)],
)
data class RawPaymentEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val rawTitle: String,
    val rawText: String,
    val amount: Long?,
    val merchant: String?,
    val detectedAt: Long,
    val foregroundApp: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** package|title|text|minute — drops reposts of the same notification. */
    val fingerprint: String,
    val parseStatus: ParseStatus,
)

enum class ParseStatus { SUCCESS, FAILED }
