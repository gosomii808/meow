package com.myfamily.meow.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: ExpenseTransaction): Long

    @Update
    suspend fun update(transaction: ExpenseTransaction)

    @Query("UPDATE transactions SET status = :status, confirmedAt = :confirmedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: TransactionStatus, confirmedAt: Long?)

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY transactionTime ASC")
    fun observePending(): Flow<List<ExpenseTransaction>>

    /** Rows reviewed (or manually added) at or after [since], by transaction time. */
    @Query("SELECT * FROM transactions WHERE status != 'PENDING' AND confirmedAt >= :since ORDER BY transactionTime ASC")
    fun observeReviewedSince(since: Long): Flow<List<ExpenseTransaction>>

    @Query(
        "SELECT * FROM transactions WHERE status = 'INCLUDED' " +
            "AND transactionTime >= :start AND transactionTime < :end ORDER BY transactionTime ASC"
    )
    fun observeIncludedBetween(start: Long, end: Long): Flow<List<ExpenseTransaction>>
}
