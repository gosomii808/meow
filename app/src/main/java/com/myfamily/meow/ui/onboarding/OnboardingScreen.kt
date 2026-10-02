package com.myfamily.meow.ui.onboarding

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.myfamily.meow.R
import com.myfamily.meow.notification.NotificationAccess
import com.myfamily.meow.reminder.DailyReviewScheduler
import com.myfamily.meow.reminder.ReminderSettings
import com.myfamily.meow.ui.common.BrandLockup
import com.myfamily.meow.ui.common.CatFace
import com.myfamily.meow.ui.common.DesignButton
import com.myfamily.meow.ui.common.ReviewTimeDialog
import com.myfamily.meow.ui.common.formatReviewTime
import com.myfamily.meow.ui.common.rememberNotificationPermissionRequest
import com.myfamily.meow.ui.theme.Divider
import com.myfamily.meow.ui.theme.DotActive
import com.myfamily.meow.ui.theme.DotInactive
import com.myfamily.meow.ui.theme.Pink
import com.myfamily.meow.ui.theme.SwipeExclude
import com.myfamily.meow.ui.theme.SwipeInclude
import com.myfamily.meow.ui.theme.SwipeSettle
import com.myfamily.meow.ui.theme.TextMuted
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary
import com.myfamily.meow.ui.theme.dz
import com.myfamily.meow.ui.theme.sz
import kotlinx.coroutines.launch

private data class Tutorial(val emoji: String, val highlight: String?, val highlightColor: Color, val rest: String, val body: String)

// Copy from the Figma 튜토리얼-R/L/U/마지막 frames.
private val TUTORIALS = listOf(
    Tutorial("👉", "오른쪽", SwipeInclude, "으로 밀면", "실제 소비한 내역으로\n가계부에 기록해요."),
    Tutorial("👈", "왼쪽", SwipeExclude, "으로 밀면", "중복된 이체나 불필요한 내역은\n소비에서 제외해요."),
    Tutorial("👆", "위쪽", SwipeSettle, "으로 밀면", "정산 내역으로 처리해\n내 몫만 소비로 기록해요."),
    Tutorial("🎯", null, TextPrimary, "AI가 소비를 분석해요", "내 소비 패턴을 분석하고\n지출 습관과 절약 포인트를 알려드려요."),
)

private enum class Step { TUTORIAL, SIGNUP, PERMISSION, REMINDER }

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by remember { mutableStateOf(Step.TUTORIAL) }
    when (step) {
        Step.TUTORIAL -> TutorialPager(onFinish = { step = Step.SIGNUP })
        Step.SIGNUP -> SignupScreen(onContinue = { step = Step.PERMISSION })
        Step.PERMISSION -> PermissionPage(onNext = { step = Step.REMINDER })
        Step.REMINDER -> ReminderPage(onDone = onDone)
    }
}

@Composable
private fun TutorialPager(onFinish: () -> Unit) {
    val pager = rememberPagerState { TUTORIALS.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == TUTORIALS.lastIndex

    Column(Modifier.fillMaxSize().background(Color.White)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            TutorialPage(TUTORIALS[page])
        }
        PageIndicator(count = TUTORIALS.size, current = pager.currentPage)
        Spacer(Modifier.height(17.dz))
        DesignButton(
            text = if (last) "시작하기" else "다음",
            onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.padding(horizontal = 34.dz),
        )
        Spacer(Modifier.height(25.dz))
    }
}

@Composable
private fun TutorialPage(t: Tutorial) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(t.emoji, fontSize = 100.sz)
        Spacer(Modifier.height(20.dz))
        Text(
            buildAnnotatedString {
                if (t.highlight != null) withStyle(SpanStyle(color = t.highlightColor)) { append(t.highlight) }
                append(t.rest)
            },
            color = TextPrimary,
            fontSize = 35.sz,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(15.dz))
        Text(t.body, color = TextSecondary, fontSize = 20.sz, textAlign = TextAlign.Center)
        Spacer(Modifier.height(120.dz))
    }
}

/** Cat sitting above the current dot (dots 14px at 26px pitch). */
@Composable
private fun PageIndicator(count: Int, current: Int) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dz)) {
            repeat(count) { i ->
                Box(Modifier.size(width = 14.dz, height = 64.dz), contentAlignment = Alignment.BottomCenter) {
                    // requiredSize: the cat is wider than its dot slot and overflows sideways.
                    if (i == current) CatFace(64.dz, Modifier.requiredSize(64.dz))
                }
            }
        }
        Spacer(Modifier.height(5.dz))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dz)) {
            repeat(count) { i ->
                Box(Modifier.size(14.dz).clip(CircleShape).background(if (i == current) DotActive else DotInactive))
            }
        }
    }
}

/** Figma 회원가입 frame. Sign-in is not implemented yet; every button just continues. */
@Composable
private fun SignupScreen(onContinue: () -> Unit) {
    var email by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(Color.White).padding(horizontal = 40.dz),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(55.dz))
        BrandLockup(catPx = 77, fontPx = 35)
        Spacer(Modifier.height(120.dz))
        Text("계정 만들기", color = TextPrimary, fontSize = 24.sz, fontWeight = FontWeight.SemiBold)
        Text("이 앱에 가입하려면 이메일을 입력하세요", color = TextPrimary, fontSize = 20.sz)
        Spacer(Modifier.height(26.dz))
        BasicTextField(
            value = email,
            onValueChange = { email = it },
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sz, color = TextPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dz)
                .clip(RoundedCornerShape(8.dz))
                .border(1.dz, Color(0xFFE0E0E0), RoundedCornerShape(8.dz))
                .background(Color.White)
                .padding(horizontal = 16.dz, vertical = 10.dz),
            decorationBox = { inner ->
                if (email.isEmpty()) Text("email@domain.com", color = TextMuted, fontSize = 14.sz)
                inner()
            },
        )
        Spacer(Modifier.height(16.dz))
        DesignButton("계속", onContinue, container = Color.Black, content = Color.White, heightPx = 40, fontPx = 14, radiusPx = 8)
        Spacer(Modifier.height(64.dz))
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dz), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dz).background(Divider))
            Text("또는", color = TextMuted, fontSize = 14.sz, modifier = Modifier.padding(horizontal = 8.dz))
            Box(Modifier.weight(1f).height(1.dz).background(Divider))
        }
        Spacer(Modifier.height(25.dz))
        SocialButton(R.drawable.ic_google_logo, "Google 계정으로 계속하기", onContinue)
        Spacer(Modifier.height(9.dz))
        SocialButton(R.drawable.apple_logo, "Apple 계정으로 계속하기", onContinue)
        Spacer(Modifier.weight(1f))
        Text(
            buildAnnotatedString {
                append("계속을 클릭하면 당사의 ")
                withStyle(SpanStyle(color = TextPrimary)) { append("서비스 이용 약관") }
                append(" 및 ")
                withStyle(SpanStyle(color = TextPrimary)) { append("개인정보 처리방침") }
                append("에 동의하는 것으로 간주됩니다.")
            },
            color = TextMuted,
            fontSize = 12.sz,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 22.dz),
        )
        Spacer(Modifier.height(40.dz))
    }
}

@Composable
private fun SocialButton(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dz)
            .height(43.dz)
            .clip(RoundedCornerShape(8.dz))
            .background(Color(0xFFEEEEEE))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dz))
        Spacer(Modifier.width(8.dz))
        Text(label, color = TextPrimary, fontSize = 14.sz, fontWeight = FontWeight.Medium)
    }
}

/** Same layout as the tutorial pages, for the setup steps the design doesn't show. */
@Composable
private fun SetupPage(
    emoji: String,
    title: String,
    body: String,
    button: String,
    onButton: () -> Unit,
    secondary: Pair<String, () -> Unit>? = null,
    extra: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(Color.White).padding(horizontal = 34.dz),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(emoji, fontSize = 100.sz)
        Spacer(Modifier.height(20.dz))
        Text(title, color = TextPrimary, fontSize = 32.sz, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(15.dz))
        Text(body, color = TextSecondary, fontSize = 20.sz, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dz))
        extra()
        Spacer(Modifier.weight(1.3f))
        DesignButton(button, onButton)
        if (secondary != null) {
            TextButton(onClick = secondary.second) { Text(secondary.first, color = TextMuted, fontSize = 16.sz) }
        } else {
            Spacer(Modifier.height(48.dz))
        }
    }
}

@Composable
private fun PermissionPage(onNext: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = NotificationAccess.isGranted(context)
        onPauseOrDispose { }
    }
    SetupPage(
        emoji = if (granted) "✅" else "🔔",
        title = if (granted) "준비 완료!" else "알림 접근을 허용해 주세요",
        body = if (granted) {
            "이제 결제 알림이 오면\n자동으로 소비 후보에 쌓여요"
        } else {
            "결제 알림을 읽으려면 알림 접근 권한이 필요해요.\n알림 내용은 이 기기 밖으로 나가지 않아요."
        },
        button = if (granted) "다음" else "설정에서 허용하기",
        onButton = {
            if (granted) {
                onNext()
            } else {
                try {
                    context.startActivity(NotificationAccess.settingsIntent(context))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(NotificationAccess.fallbackIntent())
                }
            }
        },
        secondary = if (granted) null else ("나중에 하기" to onNext),
    )
}

/** Spec §13: pick the daily review time and allow the reminder notification. */
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

    SetupPage(
        emoji = "⏰",
        title = "검토 시간을 정해요",
        body = "매일 이 시간에 남은 소비가 있으면\n알려드릴게요",
        button = "알림 받고 시작하기",
        onButton = { requestPermission(context) },
        secondary = "알림 없이 시작" to { finish(false) },
        extra = {
            TextButton(onClick = { picking = true }) {
                Text(formatReviewTime(time), color = Pink, fontSize = 30.sz, fontWeight = FontWeight.Bold)
            }
        },
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
