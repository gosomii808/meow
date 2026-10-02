package com.myfamily.meow.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myfamily.meow.data.entity.RawPaymentEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface RawEventDao {
    /** Returns -1 when an event with the same fingerprint already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: RawPaymentEvent): Long

    @Query("SELECT * FROM raw_payment_events ORDER BY detectedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RawPaymentEvent>>
}
