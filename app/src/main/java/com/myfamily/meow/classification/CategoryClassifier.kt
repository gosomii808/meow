package com.myfamily.meow.classification

/**
 * AI fallback in the classification chain (user history → app → rule → location → AI → 기타).
 * Implementations: [com.myfamily.meow.ai.GemmaClassifier] on-device; a cloud LLM later.
 */
interface CategoryClassifier {
    suspend fun classify(input: ClassificationInput): ClassificationResult
}

data class ClassificationInput(
    val merchant: String,
    val amount: Long,
    val transactionTime: Long,
    val foregroundApp: String? = null,
    /** Past user corrections, merchant → category, used as few-shot examples. */
    val history: List<Pair<String, Category>> = emptyList(),
)

data class ClassificationResult(
    val category: Category,
    val rawOutput: String,
)
