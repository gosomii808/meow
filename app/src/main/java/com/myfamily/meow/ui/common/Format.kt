package com.myfamily.meow.ui.common

import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormat = DateTimeFormatter.ofPattern("M/d HH:mm")

fun formatWon(amount: Long): String = numberFormat.format(amount) + "원"

/** Calendar cell label: 950 → "950", 12,300 → "12k", 1,250,000 → "125만". */
fun formatCompact(amount: Long): String = when {
    amount >= 1_000_000 -> "${amount / 10_000}만"
    amount >= 1_000 -> "${amount / 1_000}k"
    else -> amount.toString()
}

/** "08:32" for today, "10/1 08:32" otherwise. */
fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return if (time.toLocalDate() == LocalDate.now(zone)) time.format(timeFormat) else time.format(dateTimeFormat)
}

fun LocalDate.startMillis(zone: ZoneId = ZoneId.systemDefault()): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

val ExpenseTransaction.isIncome: Boolean get() = direction == Direction.INCOME

/** "5,500원" for spending, "+10,000원" for income. */
fun ExpenseTransaction.signedWon(): String = (if (isIncome) "+" else "") + formatWon(amount)
