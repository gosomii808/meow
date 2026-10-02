package com.myfamily.meow.notification.parser

data class ParsedPayment(val amount: Long, val merchant: String?)

/**
 * Generic parser for Korean card/bank/pay notifications. App-specific parsers can be
 * added in front of this once real notification formats are collected (see the debug screen).
 */
object NotificationParser {
    private val PAYMENT_KEYWORDS = Regex("승인|결제|출금|충전|이체|송금|사용|보냈|입금")
    private val AMOUNT = Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*원""")
    private val NOT_SPENT_PREFIX = Regex("""(누적|잔액|잔고|한도|포인트)\s*:?\s*$""")

    /** Card SMS / 알림톡: "... 10/02 08:32 스타벅스 강남점 누적123,400원" */
    private val CARD_DATETIME_MERCHANT = Regex("""\d{1,2}/\d{1,2}\s+\d{1,2}:\d{2}\s+(.+?)(?:\s+(?:누적|잔액).*)?$""")

    /** Toss transfers/charges: "내 토스뱅크 통장 → (주)카카오페이(카카오페이)" — recipient after the arrow. */
    private val ARROW_RECIPIENT = Regex("""→\s*(.+)$""")

    private val NOISE = Regex(
        """\[[^]]*]|\([^)]*\)|""" + AMOUNT.pattern +
            """|결제\s*완료|결제|승인|출금|입금|충전|완료|일시불|체크|신용|Web발신|[|:·•]""" +
            """|했어요|됐어요|되었습니다|하였습니다|보냈어요|받았어요|송금|이체|에서(?=\s|$)|님(?:에게|께)"""
    )

    fun looksLikePayment(title: String, text: String): Boolean = PAYMENT_KEYWORDS.containsMatchIn("$title $text")

    fun parse(title: String, text: String): ParsedPayment? {
        val amount = findAmount(text) ?: findAmount(title) ?: return null
        return ParsedPayment(amount, findMerchant(title, text))
    }

    private fun findAmount(source: String): Long? =
        AMOUNT.findAll(source)
            .firstOrNull { !NOT_SPENT_PREFIX.containsMatchIn(source.substring(0, it.range.first)) }
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toLongOrNull()

    private fun findMerchant(title: String, text: String): String? {
        text.lineSequence()
            .firstNotNullOfOrNull { CARD_DATETIME_MERCHANT.find(it.trim())?.groupValues?.get(1)?.trim() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        text.lineSequence()
            .firstNotNullOfOrNull { ARROW_RECIPIENT.find(it.trim())?.groupValues?.get(1) }
            ?.let(::clean)
            ?.let { return it }

        return clean(text) ?: clean(title)
    }

    private val PARTICLES = setOf("을", "를", "이", "가", "으로", "로")

    private fun clean(source: String): String? =
        source.replace(NOISE, " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotEmpty() && it !in PARTICLES }
            .joinToString(" ")
            .takeIf { it.isNotEmpty() && it.length <= 30 }
}
