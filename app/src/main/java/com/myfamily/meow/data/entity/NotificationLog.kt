package com.myfamily.meow.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Debug-only diagnostic log: notifications that look money-related, including the ones the
 * listener dropped, with the reason. Used to learn real formats (e.g. KakaoPay inside KakaoTalk).
 */
@Entity(tableName = "notification_log")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val decision: String,
)
