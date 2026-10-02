package com.myfamily.meow.data.repository

import com.myfamily.meow.classification.Category
import com.myfamily.meow.classification.RuleClassifier
import com.myfamily.meow.classification.TransferDetector
import com.myfamily.meow.classification.merchantKey
import com.myfamily.meow.data.db.AppDatabase
import com.myfamily.meow.data.entity.ClassificationSource
import com.myfamily.meow.data.entity.CorrectionHistory
import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.NotificationLog
import com.myfamily.meow.data.entity.ParseStatus
import com.myfamily.meow.data.entity.RawPaymentEvent
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TransactionRepository(private val db: AppDatabase) {
    private val rawDao = db.rawEventDao()
    private val txDao = db.transactionDao()
    private val correctionDao = db.correctionDao()
    private val logDao = db.notificationLogDao()

    val pending: Flow<List<ExpenseTransaction>> = txDao.observePending()

    fun reviewedSince(since: Long) = txDao.observeReviewedSince(since)

    fun reviewedBetween(start: Long, end: Long) = txDao.observeReviewedBetween(start, end)

    fun allBetween(start: Long, end: Long) = txDao.observeAllBetween(start, end)

    /** Up swipe: split [transaction] among [people] and record only my share (rounded up). */
    suspend fun settle(transaction: ExpenseTransaction, people: Int) {
        val share = (transaction.amount + people - 1) / people
        txDao.update(
            transaction.copy(
                amount = share,
                originalAmount = transaction.amount,
                splitCount = people,
                status = TransactionStatus.INCLUDED,
                confirmedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Undo any review decision by restoring the pre-swipe snapshot. */
    suspend fun restore(snapshot: ExpenseTransaction) =
        txDao.update(snapshot.copy(status = TransactionStatus.PENDING, confirmedAt = null))

    fun recentLogs(limit: Int = 50) = logDao.observeRecent(limit)

    suspend fun log(entry: NotificationLog) = logDao.insert(entry)

    suspend fun clearLogs() = logDao.clear()

    fun recentRawEvents(limit: Int = 30) = rawDao.observeRecent(limit)

    suspend fun countPending() = txDao.countPending()

    suspend fun needingAi() = txDao.needingAi()

    /** Latest correction per merchant, as (merchant, category) few-shot examples. */
    suspend fun correctionExamples(limit: Int = 10): List<Pair<String, Category>> =
        correctionDao.recent(limit).map { it.merchant to it.correctedCategory }

    /**
     * Stores the raw notification and, if it parsed, a PENDING candidate with category,
     * transfer and duplicate hints. Returns false if the notification was already recorded.
     */
    suspend fun recordNotification(
        event: RawPaymentEvent,
        sourceLabel: String,
        foregroundAppLabel: String? = null,
        foregroundCategory: Category? = null,
    ): Boolean {
        val rawId = rawDao.insert(event)
        if (rawId == -1L) return false
        if (event.parseStatus != ParseStatus.SUCCESS || event.amount == null) return true

        val merchant = event.merchant ?: sourceLabel
        val (category, classifiedBy) = classifyWithoutAi(merchant, foregroundCategory)
        val id = txDao.insert(
            ExpenseTransaction(
                rawEventId = rawId,
                amount = event.amount,
                merchant = merchant,
                transactionTime = event.detectedAt,
                sourceLabel = sourceLabel,
                predictedCategory = category,
                classificationSource = classifiedBy,
                transferLikely = TransferDetector.isTransferLike(event.rawTitle, event.rawText),
                source = TransactionSource.NOTIFICATION,
                // Spec §FR-03: note which app was open, so the user sees why it was categorized.
                memo = foregroundAppLabel?.let { "$it 앱 사용 중 결제" },
            )
        )
        markDuplicates(id, event.amount, event.detectedAt)
        return true
    }

    /**
     * Chain steps ① user history, ③ merchant rules, then ② the foreground app as a fallback
     * (fills gaps when the bank only shows a corporation name). ETC means "leave it to AI".
     */
    private suspend fun classifyWithoutAi(merchant: String, foregroundCategory: Category?): Pair<Category, ClassificationSource> {
        correctionDao.latestFor(merchantKey(merchant))?.let { return it.correctedCategory to ClassificationSource.USER }
        RuleClassifier.classify(merchant)?.let { return it to ClassificationSource.RULE }
        foregroundCategory?.let { return it to ClassificationSource.APP }
        return Category.ETC to ClassificationSource.RULE
    }

    /** Same amount within ±3 minutes ⇒ one duplicate group (spec §9). */
    private suspend fun markDuplicates(id: Long, amount: Long, time: Long) {
        val window = 3 * 60_000L
        val near = txDao.findSameAmountBetween(amount, time - window, time + window, excludeId = id)
        if (near.isEmpty()) return
        val group = near.firstNotNullOfOrNull { it.duplicateGroupId } ?: UUID.randomUUID().toString()
        txDao.setDuplicateGroup(near.map { it.id } + id, group)
    }

    suspend fun setStatus(id: Long, status: TransactionStatus) {
        val confirmedAt = if (status == TransactionStatus.PENDING) null else System.currentTimeMillis()
        txDao.setStatus(id, status, confirmedAt)
    }

    suspend fun saveAiResult(transaction: ExpenseTransaction, category: Category?) {
        txDao.update(
            if (category == null || category == Category.ETC) {
                transaction.copy(aiTried = true)
            } else {
                transaction.copy(aiTried = true, predictedCategory = category, classificationSource = ClassificationSource.AI)
            }
        )
    }

    /** Saves a user edit; a changed category is remembered and applied to similar pending rows. */
    suspend fun saveEdit(original: ExpenseTransaction, edited: ExpenseTransaction) {
        txDao.update(edited)
        if (edited.direction == Direction.EXPENSE && edited.category != original.category) {
            remember(edited.merchant, predicted = original.predictedCategory, corrected = edited.category)
        }
    }

    suspend fun addManual(transaction: ExpenseTransaction) {
        txDao.insert(
            transaction.copy(
                id = 0,
                source = TransactionSource.MANUAL,
                status = TransactionStatus.INCLUDED,
                confirmedAt = System.currentTimeMillis(),
            )
        )
        if (transaction.direction == Direction.EXPENSE && transaction.finalCategory != null && transaction.merchant != "직접 입력") {
            remember(transaction.merchant, predicted = Category.ETC, corrected = transaction.finalCategory)
        }
    }

    /** Re-decide an already reviewed row; keeps confirmedAt so it doesn't reappear in today's summary. */
    suspend fun changeReviewedStatus(transaction: ExpenseTransaction, status: TransactionStatus) =
        txDao.update(transaction.copy(status = status))

    /** Only manual rows can be deleted; notification rows are kept and excluded instead. */
    suspend fun deleteManual(transaction: ExpenseTransaction) {
        if (transaction.source == TransactionSource.MANUAL) txDao.delete(transaction)
    }

    /** Debug only. */
    suspend fun debugInsert(transactions: List<ExpenseTransaction>) = txDao.insertAll(transactions)

    suspend fun debugCount(): Int = txDao.count()

    suspend fun debugDeleteAllTransactions() = txDao.deleteAll()

    private suspend fun remember(merchant: String, predicted: Category, corrected: Category) {
        val key = merchantKey(merchant)
        correctionDao.insert(
            CorrectionHistory(
                merchant = merchant,
                merchantKey = key,
                predictedCategory = predicted,
                correctedCategory = corrected,
                createdAt = System.currentTimeMillis(),
            )
        )
        txDao.getPending()
            .filter { it.finalCategory == null && merchantKey(it.merchant) == key && it.predictedCategory != corrected }
            .forEach { txDao.update(it.copy(predictedCategory = corrected, classificationSource = ClassificationSource.USER)) }
    }
}
