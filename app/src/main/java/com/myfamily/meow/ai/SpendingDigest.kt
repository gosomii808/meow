package com.myfamily.meow.ai

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Compact Korean summary of the user's confirmed spending, given to the chatbot as its only
 * source of numbers. Everything is pre-computed here so the small on-device model doesn't
 * have to do arithmetic.
 */
object SpendingDigest {
    private val won = NumberFormat.getNumberInstance(Locale.KOREA)
    private fun w(v: Long) = won.format(v) + "원"

    private data class Row(val tx: ExpenseTransaction, val at: LocalDateTime)

    /**
     * [rows]: transactions from at least last month and this month (any status; only INCLUDED
     * spending is used). [monthlyGoal] 0 means no goal.
     */
    fun build(
        rows: List<ExpenseTransaction>,
        today: LocalDate,
        monthlyGoal: Long = 0,
        categoryGoals: Map<Category, Long> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val spent = rows
            .filter { it.status == TransactionStatus.INCLUDED && it.direction == Direction.EXPENSE }
            .map { Row(it, LocalDateTime.ofInstant(Instant.ofEpochMilli(it.transactionTime), zone)) }
        val month = YearMonth.from(today)
        val thisMonth = spent.filter { YearMonth.from(it.at) == month && !it.at.toLocalDate().isAfter(today) }
        val lastMonth = spent.filter { YearMonth.from(it.at) == month.minusMonths(1) }
        val day = today.dayOfMonth
        val total = thisMonth.sumOf { it.tx.amount }

        return buildString {
            appendLine("[오늘] ${today} (${today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})")
            appendLine("[이번 달 ${month.monthValue}월 1~${day}일] 총 ${w(total)}, ${thisMonth.size}건, 하루 평균 ${w(total / day)}")
            if (monthlyGoal > 0) {
                val pace = monthlyGoal * day / month.lengthOfMonth()
                val left = monthlyGoal - total
                appendLine(
                    "- 월 목표 ${w(monthlyGoal)}, " +
                        (if (left >= 0) "남은 금액 ${w(left)}" else "목표 초과 ${w(-left)}") +
                        ", 지금까지 목표 속도는 ${w(pace)}라서 " +
                        (if (total > pace) "${w(total - pace)} 빠르게 쓰는 중" else "${w(pace - total)} 여유 있음") +
                        (if (left > 0) ", 남은 ${month.lengthOfMonth() - day}일 동안 하루 ${w(left / (month.lengthOfMonth() - day).coerceAtLeast(1))}까지 가능" else "")
                )
            } else {
                appendLine("- 월 목표 없음")
            }

            appendLine("[이번 달 카테고리별]")
            categoryLines(thisMonth, categoryGoals).forEach { appendLine("- $it") }
            // Spelled out so "쇼핑을 줄였다면?" gets "쇼핑 기록이 없어" instead of borrowed numbers.
            val unused = Category.entries.filter { c -> c != Category.ETC && thisMonth.none { it.tx.category == c } }
            if (unused.isNotEmpty()) appendLine("[이번 달 기록 없는 카테고리 (0원)] " + unused.joinToString(", ") { it.label })

            val places = thisMonth.groupBy { it.tx.merchant }
                .map { (m, list) -> Triple(m, list.size, list.sumOf { it.tx.amount }) }
                .sortedWith(compareByDescending<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third })
                .take(5)
            if (places.isNotEmpty()) {
                appendLine("[이번 달 자주 간 곳] " + places.joinToString(" · ") { "${it.first} ${it.second}회 ${w(it.third)}" })
            }

            val recent = spent.filter { !it.at.toLocalDate().isBefore(today.minusDays(60)) }
            if (recent.isNotEmpty()) {
                val byDay = DayOfWeek.entries.associateWith { d -> recent.filter { it.at.dayOfWeek == d }.sumOf { it.tx.amount } }
                appendLine(
                    "[최근 60일 요일별] " + DayOfWeek.entries.joinToString(" · ") {
                        "${it.getDisplayName(TextStyle.SHORT, Locale.KOREAN)} ${w(byDay.getValue(it))}"
                    }
                )
                val slots = listOf("아침(6~11시)" to 6..10, "점심(11~14시)" to 11..13, "오후(14~18시)" to 14..17, "저녁(18~22시)" to 18..21)
                val slotSums = slots.map { (name, hours) -> name to recent.filter { it.at.hour in hours }.sumOf { it.tx.amount } } +
                    ("밤(22~6시)" to recent.filter { it.at.hour >= 22 || it.at.hour < 6 }.sumOf { it.tx.amount })
                appendLine("[최근 60일 시간대별] " + slotSums.joinToString(" · ") { "${it.first} ${w(it.second)}" })
            }

            if (lastMonth.isNotEmpty()) {
                val lastTotal = lastMonth.sumOf { it.tx.amount }
                val lastSameDays = lastMonth.filter { it.at.dayOfMonth <= day }.sumOf { it.tx.amount }
                appendLine(
                    "[지난달 ${month.minusMonths(1).monthValue}월] 총 ${w(lastTotal)}, 같은 기간(1~${day}일) ${w(lastSameDays)} → 이번 달은 " +
                        if (total >= lastSameDays) "${w(total - lastSameDays)} 더 씀" else "${w(lastSameDays - total)} 덜 씀"
                )
                appendLine("- 지난달 카테고리: " + categoryLines(lastMonth, emptyMap()).take(4).joinToString(" · "))
            } else {
                appendLine("[지난달] 기록 없음")
            }

            val latest = thisMonth.sortedByDescending { it.at }.take(8)
            if (latest.isNotEmpty()) {
                appendLine("[최근 결제]")
                latest.forEach {
                    appendLine("- ${it.at.monthValue}/${it.at.dayOfMonth} ${"%02d:%02d".format(it.at.hour, it.at.minute)} ${it.tx.merchant} ${w(it.tx.amount)} (${it.tx.category.label})")
                }
            }
        }.trimEnd()
    }

    private fun categoryLines(rows: List<Row>, goals: Map<Category, Long>): List<String> {
        val total = rows.sumOf { it.tx.amount }.coerceAtLeast(1)
        return rows.groupBy { it.tx.category }
            .map { (c, list) -> c to list }
            .sortedByDescending { (_, list) -> list.sumOf { it.tx.amount } }
            .map { (c, list) ->
                val sum = list.sumOf { it.tx.amount }
                val goal = goals[c]
                "${c.label} ${w(sum)} (${list.size}건, ${sum * 100 / total}%)" +
                    if (goal != null) ", 목표 ${w(goal)} 중 ${if (sum <= goal) "${w(goal - sum)} 남음" else "${w(sum - goal)} 초과"}" else ""
            }
            .ifEmpty { listOf("기록 없음") }
    }
}
