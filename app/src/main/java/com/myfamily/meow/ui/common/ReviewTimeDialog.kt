package com.myfamily.meow.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reviewTimeFormat = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

/** "오후 10:00" */
fun formatReviewTime(time: LocalTime): String = time.format(reviewTimeFormat)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("검토 시간") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
