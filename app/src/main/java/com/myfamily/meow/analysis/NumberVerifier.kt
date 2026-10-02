package com.myfamily.meow.analysis

/**
 * Guards against the small model inventing numbers (spec G / §1-3). It extracts every numeric
 * expression from a model answer and checks each against the set of numbers the app actually gave
 * the model (tool results + insight figures). Amounts are normalized to won, and values rounded to
 * the nearest 1,000 or 10,000 are accepted (the cat may say "약 2만원" for 19,800원).
 *
 * This only reports what's unverified; the retry-then-template fallback lives in the chat pipeline.
 * Pure Kotlin for JUnit.
 *
 * Recognized formats: "12,000원", "12000원", "1만 2천원", "1.2만원", "23%", "2.3배", "5번", "3건".
 */
object NumberVerifier {
    data class Result(
        val ok: Boolean,
        /** Normalized tokens found in the answer that aren't backed by an allowed number. */
        val unverified: List<String>,
    )

    // Korean-unit amounts first so "만/천" aren't misread by the plain-digit rule.
    private val WON_KR = Regex("""(\d+(?:\.\d+)?)\s*만(?:\s*(\d+)\s*천)?\s*원""")
    private val WON_CHUN = Regex("""(\d+)\s*천\s*원""")
    private val WON_PLAIN = Regex("""([\d,]+)\s*원""")
    private val PERCENT = Regex("""(\d+(?:\.\d+)?)\s*%""")
    private val MULTIPLE = Regex("""(\d+(?:\.\d+)?)\s*배""")
    private val COUNT = Regex("""(\d+)\s*[번건]""")

    /** Normalized number tokens found in [text]. */
    fun extract(text: String): List<String> {
        val tokens = mutableListOf<String>()
        var rest = text

        fun consume(regex: Regex, toToken: (MatchResult) -> String?) {
            val sb = StringBuilder()
            var last = 0
            for (m in regex.findAll(rest)) {
                toToken(m)?.let { tokens += it }
                sb.append(rest, last, m.range.first).append(' ')
                last = m.range.last + 1
            }
            sb.append(rest, last, rest.length)
            rest = sb.toString()
        }

        consume(WON_KR) { m ->
            val man = m.groupValues[1].toDouble()
            val chun = m.groupValues[2].ifEmpty { "0" }.toLong()
            wonToken((man * 10_000).toLong() + chun * 1_000)
        }
        consume(WON_CHUN) { m -> wonToken(m.groupValues[1].toLong() * 1_000) }
        consume(WON_PLAIN) { m -> m.groupValues[1].replace(",", "").toLongOrNull()?.let { wonToken(it) } }
        // These units never collide with 원, so run them on the original text.
        for (m in PERCENT.findAll(text)) tokens += "P${trimNum(m.groupValues[1])}"
        for (m in MULTIPLE.findAll(text)) tokens += "X${trimNum(m.groupValues[1])}"
        for (m in COUNT.findAll(text)) tokens += "N${m.groupValues[1]}"
        return tokens
    }

    fun verify(answer: String, allowed: Set<String>): Result {
        val allowedTokens = HashSet<String>()
        for (a in allowed) {
            for (t in extract(a)) {
                allowedTokens += t
                // Accept thousand/ten-thousand rounded variants of allowed amounts.
                if (t.startsWith("W")) {
                    val v = t.substring(1).toLong()
                    allowedTokens += wonToken(roundTo(v, 1_000))
                    allowedTokens += wonToken(roundTo(v, 10_000))
                }
            }
        }
        val unverified = extract(answer).filter { token ->
            if (token in allowedTokens) return@filter false
            // An answer amount may itself be a rounded form of an allowed exact amount.
            if (token.startsWith("W")) {
                val v = token.substring(1).toLong()
                if (wonToken(roundTo(v, 1_000)) in allowedTokens || wonToken(roundTo(v, 10_000)) in allowedTokens) return@filter false
            }
            true
        }.distinct()
        return Result(unverified.isEmpty(), unverified)
    }

    private fun wonToken(v: Long) = "W$v"
    private fun roundTo(v: Long, unit: Long) = Math.round(v.toDouble() / unit) * unit
    private fun trimNum(s: String): String {
        val d = s.toDouble()
        return if (d == d.toLong().toDouble()) d.toLong().toString() else s
    }
}
