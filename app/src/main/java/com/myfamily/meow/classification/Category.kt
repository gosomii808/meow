package com.myfamily.meow.classification

enum class Category(val label: String, val emoji: String) {
    FOOD("식비", "🍚"),
    CAFE("카페", "☕"),
    TRANSPORT("교통비", "🚌"),
    SHOPPING("쇼핑", "🛍️"),
    LIVING("생활", "🏠"),
    CULTURE("문화/여가", "🎬"),
    EDUCATION("교육", "📚"),
    MEDICAL("의료", "💊"),
    TRAVEL("여행", "✈️"),
    SUBSCRIPTION("구독", "⭐"),
    ETC("기타", "📦");

    companion object {
        /**
         * Maps raw LLM output to a category. Exact label match first, then the earliest
         * label mentioned anywhere in the output (models sometimes answer "카페입니다").
         * Anything else falls back to [ETC].
         */
        fun fromModelOutput(raw: String): Category {
            val text = raw.trim().trim('.', '"', '\'', '*', '`', ' ')
            entries.firstOrNull { it.label == text }?.let { return it }
            return entries
                .mapNotNull { category -> text.indexOf(category.label).takeIf { it >= 0 }?.let { category to it } }
                .minByOrNull { it.second }
                ?.first
                ?: ETC
        }
    }
}
