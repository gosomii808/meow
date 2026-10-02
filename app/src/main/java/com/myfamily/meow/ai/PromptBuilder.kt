package com.myfamily.meow.ai

import com.myfamily.meow.classification.Category
import com.myfamily.meow.classification.ClassificationInput
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object PromptBuilder {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun categoryPrompt(input: ClassificationInput, zone: ZoneId = ZoneId.systemDefault()): String {
        val amount = NumberFormat.getNumberInstance(Locale.KOREA).format(input.amount)
        val time = Instant.ofEpochMilli(input.transactionTime).atZone(zone).format(timeFormat)
        val history = if (input.history.isEmpty()) {
            "없음"
        } else {
            input.history.joinToString("\n") { (merchant, category) -> "$merchant → ${category.label}" }
        }

        return """
            |다음 결제 내역의 소비 카테고리를 분류해.
            |
            |가맹점: ${input.merchant}
            |결제 금액: ${amount}원
            |시간: $time
            |직전 앱: ${input.foregroundApp ?: "없음"}
            |
            |과거 사용자 기록:
            |$history
            |
            |다음 카테고리 중 하나만 선택:
            |${Category.entries.joinToString(", ") { it.label }}
            |
            |카테고리 이름만 답해.
        """.trimMargin()
    }
}
