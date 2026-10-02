package com.myfamily.meow.ai

import android.content.Context
import com.myfamily.meow.classification.ClassificationInput
import com.myfamily.meow.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Step ⑤ of the classification chain. Runs Gemma over pending rows that history and rules
 * left as 기타. App-scoped so the ~5 s model load happens once per process.
 */
class AiCategorizer(context: Context, private val repository: TransactionRepository) {
    private val classifier = GemmaClassifier(context)
    private val mutex = Mutex()

    private val _runningIds = MutableStateFlow<Set<Long>>(emptySet())
    /** Rows currently queued for or being classified, for the "AI 분류 중…" caption. */
    val runningIds = _runningIds.asStateFlow()

    val isAvailable: Boolean get() = classifier.modelFile.exists()

    suspend fun classifyPending() {
        if (!isAvailable) return
        mutex.withLock {
            val targets = repository.needingAi()
            if (targets.isEmpty()) return
            _runningIds.value = targets.map { it.id }.toSet()
            try {
                val examples = repository.correctionExamples()
                for (tx in targets) {
                    val result = runCatching {
                        classifier.classify(
                            ClassificationInput(
                                merchant = tx.merchant,
                                amount = tx.amount,
                                transactionTime = tx.transactionTime,
                                foregroundApp = null,
                                history = examples,
                            )
                        )
                    }.getOrNull()
                    repository.saveAiResult(tx, result?.category)
                    _runningIds.value -= tx.id
                }
            } finally {
                _runningIds.value = emptySet()
            }
        }
    }
}
