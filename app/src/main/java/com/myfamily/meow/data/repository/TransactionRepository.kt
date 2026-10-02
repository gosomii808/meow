package com.myfamily.meow.data.repository

import com.myfamily.meow.data.db.AppDatabase
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.ParseStatus
import com.myfamily.meow.data.entity.RawPaymentEvent
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val db: AppDatabase) {
    private val rawDao = db.rawEventDao()
    private val txDao = db.transactionDao()

    val pending: Flow<List<ExpenseTransaction>> = txDao.observePending()

    fun reviewedSince(since: Long) = txDao.observeReviewedSince(since)

    fun includedBetween(start: Long, end: Long) = txDao.observeIncludedBetween(start, end)

    fun recentRawEvents(limit: Int = 30) = rawDao.observeRecent(limit)

    /**
     * Stores the raw notification and, if it parsed, a PENDING candidate.
     * Returns false if the same notification was already recorded.
     */
    suspend fun recordNotification(event: RawPaymentEvent, sourceLabel: String): Boolean {
        val rawId = rawDao.insert(event)
        if (rawId == -1L) return false
        if (event.parseStatus == ParseStatus.SUCCESS && event.amount != null) {
            txDao.insert(
                ExpenseTransaction(
                    rawEventId = rawId,
                    amount = event.amount,
                    merchant = event.merchant ?: sourceLabel,
                    transactionTime = event.detectedAt,
                    sourceLabel = sourceLabel,
                    source = TransactionSource.NOTIFICATION,
                )
            )
        }
        return true
    }

    suspend fun setStatus(id: Long, status: TransactionStatus) {
        val confirmedAt = if (status == TransactionStatus.PENDING) null else System.currentTimeMillis()
        txDao.setStatus(id, status, confirmedAt)
    }

    suspend fun update(transaction: ExpenseTransaction) = txDao.update(transaction)

    suspend fun addManual(transaction: ExpenseTransaction) = txDao.insert(
        transaction.copy(
            id = 0,
            source = TransactionSource.MANUAL,
            status = TransactionStatus.INCLUDED,
            confirmedAt = System.currentTimeMillis(),
        )
    )
}
