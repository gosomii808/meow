package com.myfamily.meow.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.myfamily.meow.data.entity.NotificationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {
    @Insert
    suspend fun insert(log: NotificationLog)

    @Query("SELECT * FROM notification_log ORDER BY postedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationLog>>

    @Query("DELETE FROM notification_log")
    suspend fun clear()
}
