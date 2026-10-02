package com.myfamily.meow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.myfamily.meow.aiCategorizer
import com.myfamily.meow.notification.NotificationAccess
import com.myfamily.meow.reminder.DailyReviewScheduler
import com.myfamily.meow.reminder.ReminderSettings
import com.myfamily.meow.ui.common.ReviewTimeDialog
import com.myfamily.meow.ui.common.canPostNotifications
import com.myfamily.meow.ui.common.formatReviewTime
import com.myfamily.meow.ui.common.rememberNotificationPermissionRequest
import com.myfamily.meow.ui.theme.Mint
import com.myfamily.meow.ui.theme.OnMint
import com.myfamily.meow.ui.theme.Surface
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { ReminderSettings(context) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var time by remember { mutableStateOf(settings.time) }
    var pickingTime by remember { mutableStateOf(false) }
    var listenerOn by remember { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        listenerOn = NotificationAccess.isGranted(context)
        canNotify = canPostNotifications(context)
        onPauseOrDispose { }
    }

    fun apply(newEnabled: Boolean = enabled) {
        settings.enabled = newEnabled
        enabled = newEnabled
        DailyReviewScheduler.schedule(context)
    }
    val requestPermission = rememberNotificationPermissionRequest { granted ->
        canNotify = granted
        apply(granted)
    }

    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("설정", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)

        SettingRow(
            title = "하루 검토 알림",
            subtitle = if (enabled && !canNotify) "알림 권한이 없어 울리지 않아요" else "남은 소비가 있을 때만 알려줘요",
        ) {
            Switch(
                checked = enabled && canNotify,
                onCheckedChange = { on -> if (on) requestPermission(context) else apply(false) },
                colors = SwitchDefaults.colors(checkedTrackColor = Mint, checkedThumbColor = OnMint),
            )
        }
        SettingRow(
            title = "검토 시간",
            subtitle = "정확한 시각보다 몇 분 늦을 수 있어요",
            onClick = { pickingTime = true },
        ) {
            Text(formatReviewTime(time), color = Mint, fontWeight = FontWeight.Bold)
        }
        SettingRow(
            title = "알림 접근",
            subtitle = "결제 알림 수집에 필요해요",
            onClick = { context.startActivity(NotificationAccess.fallbackIntent()) },
        ) {
            Text(if (listenerOn) "켜짐" else "꺼짐", color = if (listenerOn) Mint else TextSecondary)
        }
        SettingRow(
            title = "온디바이스 AI 분류",
            subtitle = "규칙으로 못 정한 가맹점을 Gemma가 분류해요",
        ) {
            Text(if (context.aiCategorizer.isAvailable) "모델 있음" else "모델 없음", color = TextSecondary)
        }
    }

    if (pickingTime) {
        ReviewTimeDialog(
            initial = time,
            onDismiss = { pickingTime = false },
            onConfirm = {
                settings.time = it
                time = it
                pickingTime = false
                apply()
            },
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp)
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
        trailing()
    }
}
