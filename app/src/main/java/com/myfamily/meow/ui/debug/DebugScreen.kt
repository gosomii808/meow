package com.myfamily.meow.ui.debug

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.myfamily.meow.debug.FakePaymentNotifier
import com.myfamily.meow.notification.Diagnostics
import com.myfamily.meow.notification.NotificationAccess
import com.myfamily.meow.reminder.DailyReviewScheduler
import com.myfamily.meow.repository
import com.myfamily.meow.ui.common.formatTime
import com.myfamily.meow.ui.theme.Surface
import com.myfamily.meow.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Debug-build tools: fake payment notifications, captured raw notifications, AI test. */
@Composable
fun DebugScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showAi by remember { mutableStateOf(false) }
    if (showAi) {
        AiTestScreen(modifier)
        return
    }

    var listenerOn by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    LifecycleResumeEffect(Unit) {
        listenerOn = NotificationAccess.isGranted(context)
        onPauseOrDispose { }
    }
    var pendingFakes by remember { mutableStateOf<List<FakePaymentNotifier.Fake>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pendingFakes?.let { if (ok) FakePaymentNotifier.post(context, it) }
        pendingFakes = null
    }

    fun post(fakes: List<FakePaymentNotifier.Fake>) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingFakes = fakes
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            FakePaymentNotifier.post(context, fakes)
        }
    }

    val rawEvents by remember { context.repository.recentRawEvents() }.collectAsState(initial = emptyList())
    val logs by remember { context.repository.recentLogs() }.collectAsState(initial = emptyList())
    var diagnosticsOn by remember { mutableStateOf(Diagnostics.isEnabled(context)) }
    val scope = rememberCoroutineScope()

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("개발자 도구", style = MaterialTheme.typography.headlineSmall)
        Text("알림 접근: ${if (listenerOn) "켜짐 ✅" else "꺼짐 ❌ (가짜 알림도 수집 안 됨)"}")
        if (!listenerOn) {
            OutlinedButton(onClick = { context.startActivity(NotificationAccess.fallbackIntent()) }) {
                Text("알림 접근 설정 열기")
            }
        }

        HorizontalDivider()
        Text("더미 데이터 (리포트 그래프 테스트용)", style = MaterialTheme.typography.titleMedium)
        var dummyMsg by remember { mutableStateOf("") }
        Button(onClick = {
            scope.launch {
                val rows = com.myfamily.meow.debug.DummyData.generate()
                context.repository.debugInsert(rows)
                dummyMsg = "${rows.size}건 추가됨 (총 ${context.repository.debugCount()}건)"
            }
        }) { Text("지난 3개월치 더미 소비 넣기") }
        var confirmWipe by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { confirmWipe = true }) { Text("모든 소비 기록 삭제 (원본 알림은 유지)") }
        if (dummyMsg.isNotEmpty()) Text(dummyMsg, color = TextSecondary, fontSize = 13.sp)

        if (confirmWipe) {
            AlertDialog(
                onDismissRequest = { confirmWipe = false },
                title = { Text("모든 소비 기록 삭제") },
                text = { Text("검토·내역·리포트의 모든 소비 기록이 지워져요. 원본 알림(raw)은 유지됩니다. 되돌릴 수 없어요.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmWipe = false
                        scope.launch {
                            val before = context.repository.debugCount()
                            context.repository.debugDeleteAllTransactions()
                            dummyMsg = "${before}건 삭제됨"
                        }
                    }) { Text("삭제") }
                },
                dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("취소") } },
            )
        }

        HorizontalDivider()
        Text("가짜 결제 알림 보내기", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { post(FakePaymentNotifier.CHARGE_SCENARIO) }) { Text("출금 → 충전 → 결제 (10,000원 ×3)") }
        (FakePaymentNotifier.SINGLES + FakePaymentNotifier.SETTLEMENT).forEach { fake ->
            OutlinedButton(onClick = { post(listOf(fake)) }) { Text("${fake.sourceLabel}: ${fake.text.lines().last().take(24)}…") }
        }

        OutlinedButton(onClick = { DailyReviewScheduler.runNow(context) }) { Text("검토 알림 지금 보내기 (남은 건 있을 때)") }
        OutlinedButton(onClick = { showAi = true }) { Text("Gemma 분류 테스트 열기") }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("알림 진단 모드", style = MaterialTheme.typography.titleMedium)
                Text(
                    "모든 앱의 돈 관련 알림을 버린 이유와 함께 기록해요 (기기 안에만 저장)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(checked = diagnosticsOn, onCheckedChange = {
                Diagnostics.setEnabled(context, it)
                diagnosticsOn = it
            })
        }
        if (logs.isNotEmpty()) {
            OutlinedButton(onClick = { scope.launch { context.repository.clearLogs() } }) { Text("진단 기록 지우기 (${logs.size})") }
        }
        logs.forEach { log ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .padding(10.dp),
            ) {
                Text("${formatTime(log.postedAt)} · ${log.packageName}", color = TextSecondary, fontSize = 12.sp)
                Text(log.title, fontSize = 14.sp)
                Text(log.text, fontSize = 13.sp, color = TextSecondary)
                Text("→ ${log.decision}", fontSize = 13.sp)
            }
        }

        HorizontalDivider()
        Text("최근 수집된 원본 알림 (${rawEvents.size})", style = MaterialTheme.typography.titleMedium)
        rawEvents.forEach { e ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .padding(10.dp),
            ) {
                Text("${formatTime(e.detectedAt)} · ${e.packageName} · ${e.parseStatus}", color = TextSecondary, fontSize = 12.sp)
                Text(e.rawTitle, fontSize = 14.sp)
                Text(e.rawText, fontSize = 13.sp, color = TextSecondary)
                Text("→ ${e.amount ?: "-"}원 / ${e.merchant ?: "-"}", fontSize = 13.sp)
            }
        }
    }
}
