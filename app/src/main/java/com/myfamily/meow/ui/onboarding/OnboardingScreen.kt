package com.myfamily.meow.ui.onboarding

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.myfamily.meow.notification.NotificationAccess
import com.myfamily.meow.reminder.DailyReviewScheduler
import com.myfamily.meow.reminder.ReminderSettings
import com.myfamily.meow.ui.common.ReviewTimeDialog
import com.myfamily.meow.ui.common.formatReviewTime
import com.myfamily.meow.ui.common.rememberNotificationPermissionRequest
import com.myfamily.meow.ui.common.Wordmark
import com.myfamily.meow.ui.theme.Mint
import com.myfamily.meow.ui.theme.OnMint
import com.myfamily.meow.ui.theme.Outline
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary

private data class Page(val emoji: String, val title: String, val body: String)

private val PAGES = listOf(
    Page("💳", "결제 알림을 모아요", "카드·은행·간편결제 알림을 자동으로 모아\n하루 한 번 직접 확인해요"),
    Page("👈", "왼쪽으로 밀면", "중복된 이체나 불필요한 내역은\n왼쪽으로 밀어서 제외해요"),
    Page("👉", "오른쪽으로 밀면", "실제 소비한 내역은\n오른쪽으로 밀어서 가계부에 기록해요"),
    Page("📅", "캘린더로 확인", "날짜별 소비 내역과\n월별 총 지출을 한눈에 확인해요"),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }

    if (page > PAGES.size) {
        ReminderPage(onDone = onDone)
    } else if (page < PAGES.size) {
        val p = PAGES[page]
        OnboardingLayout(
            indicator = { PageIndicator(count = PAGES.size, current = page) },
            emoji = p.emoji,
            title = p.title,
            body = p.body,
            button = "다음",
            onButton = { page++ },
        )
    } else {
        PermissionPage(onDone = { page++ })
    }
}

/** Spec §13 onboarding: pick the daily review time and allow the reminder notification. */
@Composable
private fun ReminderPage(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { ReminderSettings(context) }
    var time by remember { mutableStateOf(settings.time) }
    var picking by remember { mutableStateOf(false) }

    fun finish(enabled: Boolean) {
        settings.time = time
        settings.enabled = enabled
        DailyReviewScheduler.schedule(context)
        onDone()
    }
    val requestPermission = rememberNotificationPermissionRequest { granted -> finish(granted) }

    OnboardingLayout(
        indicator = {},
        emoji = "⏰",
        title = "검토 시간을 정해요",
        body = "매일 이 시간에 남은 소비가 있으면\n알려드릴게요",
        extra = {
            TextButton(onClick = { picking = true }) {
                Text(formatReviewTime(time), color = Mint, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        },
        button = "알림 받고 시작하기",
        onButton = { requestPermission(context) },
        secondary = "알림 없이 시작" to { finish(false) },
    )

    if (picking) {
        ReviewTimeDialog(
            initial = time,
            onDismiss = { picking = false },
            onConfirm = {
                time = it
                picking = false
            },
        )
    }
}

@Composable
private fun PermissionPage(onDone: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = NotificationAccess.isGranted(context)
        onPauseOrDispose { }
    }

    OnboardingLayout(
        indicator = {},
        emoji = if (granted) "✅" else "🔔",
        title = if (granted) "준비 완료!" else "알림 접근을 허용해 주세요",
        body = if (granted) {
            "이제 결제 알림이 오면\n자동으로 소비 후보에 쌓여요"
        } else {
            "결제 알림을 읽으려면 알림 접근 권한이 필요해요.\n알림 내용은 이 기기 밖으로 나가지 않아요."
        },
        button = if (granted) "시작하기" else "설정에서 허용하기",
        onButton = {
            if (granted) {
                onDone()
            } else {
                try {
                    context.startActivity(NotificationAccess.settingsIntent(context))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(NotificationAccess.fallbackIntent())
                }
            }
        },
        secondary = if (granted) null else ("나중에 하기" to onDone),
    )
}

@Composable
private fun OnboardingLayout(
    indicator: @Composable () -> Unit,
    emoji: String,
    title: String,
    body: String,
    button: String,
    onButton: () -> Unit,
    secondary: Pair<String, () -> Unit>? = null,
    extra: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Wordmark()
        Spacer(Modifier.height(40.dp))
        indicator()
        Spacer(Modifier.height(48.dp))
        Text(emoji, fontSize = 72.sp)
        Spacer(Modifier.height(40.dp))
        Text(title, color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Text(body, color = TextSecondary, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 26.sp)
        Spacer(Modifier.height(16.dp))
        extra()
        Spacer(Modifier.weight(1.4f))
        Button(
            onClick = onButton,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = OnMint),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(button, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (secondary != null) {
            TextButton(onClick = secondary.second) { Text(secondary.first, color = TextSecondary) }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .width(if (i == current) 36.dp else 12.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i == current) Mint else Outline),
            )
        }
    }
}
