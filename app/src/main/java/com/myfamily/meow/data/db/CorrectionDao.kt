package com.myfamily.meow.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.myfamily.meow.data.entity.CorrectionHistory

@Dao
interface CorrectionDao {
    @Insert
    suspend fun insert(correction: CorrectionHistory)

    @Query("SELECT * FROM correction_history WHERE merchantKey = :key ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestFor(key: String): CorrectionHistory?

    /** Most recent correction per merchant, used as few-shot examples for the AI prompt. */
    @Query(
        "SELECT * FROM correction_history WHERE id IN " +
            "(SELECT MAX(id) FROM correction_history GROUP BY merchantKey) ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun recent(limit: Int): List<CorrectionHistory>
}
