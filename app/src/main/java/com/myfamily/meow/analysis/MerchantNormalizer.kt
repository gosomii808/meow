package com.myfamily.meow.analysis

/**
 * Normalizes a raw merchant name so the same place matches across branches and formats
 * (spec A / §1-4). Pure Kotlin, no Android dependencies.
 *
 * Examples: "스타벅스 강남R점" → "스타벅스", "(주)스타벅스코리아" → "스타벅스코리아",
 * "GS25 역삼역점" → "gs25", "CU편의점" → "cu편의점", "이마트 본점" → "이마트".
 */
object MerchantNormalizer {
    private val COMPANY = Regex("㈜|\\(주\\)|\\（주\\）|주식회사|유한회사")
    private val PARENS = Regex("[\\(（][^)）]*[)）]")
    private val SPECIAL = Regex("[^0-9a-z가-힣\\s]") // applied after lowercasing
    private val SPACES = Regex("\\s+")

    // A trailing space-separated token that denotes a branch, not the brand.
    private val BRANCH_SUFFIX = Regex(".+(호점|지점|점)$")
    private val BRANCH_WORDS = setOf("본점", "지점")

    // Words that end in "점" but are store types, not branches — keep them.
    private val STORE_TYPES = setOf("편의점", "백화점", "면세점", "할인점", "음식점", "대리점", "직영점")

    fun normalize(raw: String): String {
        var s = raw.lowercase().trim()
        s = COMPANY.replace(s, " ")
        s = PARENS.replace(s, " ")
        s = SPECIAL.replace(s, " ")
        s = SPACES.replace(s, " ").trim()
        if (s.isEmpty()) return raw.trim()

        val tokens = s.split(" ").toMutableList()
        while (tokens.size > 1) {
            val last = tokens.last()
            val isBranch = (last in BRANCH_WORDS) ||
                (BRANCH_SUFFIX.matches(last) && last !in STORE_TYPES)
            if (isBranch) tokens.removeAt(tokens.lastIndex) else break
        }
        return tokens.joinToString(" ").trim().ifEmpty { raw.trim() }
    }
}
