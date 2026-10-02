package com.myfamily.meow.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ClassificationSource
import com.myfamily.meow.data.entity.Direction
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Extra button in the edit dialog, e.g. "제외하기" for an already reviewed row. */
data class EditAction(val label: String, val color: Color? = null, val onClick: () -> Unit)

/**
 * Edits an existing row ([initial] non-null) or creates a manual record on [date].
 * Amount and category are required (spec §15).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionEditDialog(
    initial: ExpenseTransaction?,
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (ExpenseTransaction) -> Unit,
    actions: List<EditAction> = emptyList(),
) {
    val zone = ZoneId.systemDefault()
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val initialTime = initial?.let { Instant.ofEpochMilli(it.transactionTime).atZone(zone).toLocalTime() }
        ?: LocalTime.now()

    var amount by remember { mutableStateOf(initial?.amount?.toString().orEmpty()) }
    var merchant by remember { mutableStateOf(initial?.merchant.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category) }
    var time by remember { mutableStateOf(initialTime.format(timeFormat)) }
    var memo by remember { mutableStateOf(initial?.memo.orEmpty()) }

    val parsedAmount = amount.toLongOrNull()?.takeIf { it > 0 }
    val parsedTime = runCatching { LocalTime.parse(time, timeFormat) }.getOrNull()
    val canSave = parsedAmount != null && category != null && parsedTime != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "직접 기록하기" else "내역 수정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit) },
                    label = { Text("금액 *") },
                    suffix = { Text("원") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("가맹점") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("카테고리 *", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("시간 (HH:mm)") },
                    isError = parsedTime == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (actions.isNotEmpty()) {
                    HorizontalDivider()
                    actions.forEach { action ->
                        TextButton(onClick = action.onClick) {
                            Text(action.label, color = action.color ?: MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val day = initial?.transactionTime?.toLocalDate(zone) ?: date
                    val millis = day.atTime(parsedTime!!).atZone(zone).toInstant().toEpochMilli()
                    val base = initial ?: ExpenseTransaction(
                        amount = 0,
                        merchant = "",
                        transactionTime = millis,
                        sourceLabel = "직접 입력",
                        source = TransactionSource.MANUAL,
                    )
                    val categoryChanged = category != base.category || initial == null
                    onSave(
                        base.copy(
                            direction = Direction.EXPENSE,
                            amount = parsedAmount!!,
                            merchant = merchant.trim().ifEmpty { "직접 입력" },
                            transactionTime = millis,
                            finalCategory = category,
                            classificationSource = if (categoryChanged) ClassificationSource.USER else base.classificationSource,
                            memo = memo.trim().ifEmpty { null },
                        )
                    )
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
