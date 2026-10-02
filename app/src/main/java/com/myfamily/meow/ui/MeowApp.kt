package com.myfamily.meow.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.myfamily.meow.BuildConfig
import com.myfamily.meow.notification.NotificationAccess
import com.myfamily.meow.ui.calendar.CalendarScreen
import com.myfamily.meow.ui.common.Wordmark
import com.myfamily.meow.ui.debug.DebugScreen
import com.myfamily.meow.ui.onboarding.OnboardingScreen
import com.myfamily.meow.ui.review.ReviewScreen
import com.myfamily.meow.ui.theme.Background
import com.myfamily.meow.ui.theme.CoralDark
import com.myfamily.meow.ui.theme.Mint
import com.myfamily.meow.ui.theme.Outline
import com.myfamily.meow.ui.theme.TextPrimary
import com.myfamily.meow.ui.theme.TextSecondary

private const val PREFS = "meow_prefs"
private const val KEY_ONBOARDED = "onboarding_done"

private enum class Tab(val label: String, val icon: ImageVector) {
    REVIEW("검토", Icons.Default.CheckCircle),
    HISTORY("내역", Icons.Default.DateRange),
}

@Composable
fun MeowApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (!onboarded) {
            OnboardingScreen(onDone = {
                prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                onboarded = true
            })
        } else {
            MainScreen()
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.REVIEW) }
    var showDebug by rememberSaveable { mutableStateOf(false) }
    var listenerOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        listenerOn = NotificationAccess.isGranted(context)
        onPauseOrDispose { }
    }
    BackHandler(enabled = showDebug) { showDebug = false }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Wordmark(Modifier.align(Alignment.Center))
            if (BuildConfig.DEBUG) {
                TextButton(onClick = { showDebug = !showDebug }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text(if (showDebug) "닫기" else "DEV", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        if (!listenerOn && !showDebug) {
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CoralDark)
                    .clickable { context.startActivity(NotificationAccess.fallbackIntent()) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("알림 접근이 꺼져 있어 결제를 수집하지 못해요. 눌러서 켜기", color = TextPrimary, fontSize = 14.sp)
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                showDebug -> DebugScreen()
                tab == Tab.REVIEW -> ReviewScreen()
                else -> CalendarScreen()
            }
        }

        HorizontalDivider(color = Outline)
        Row(Modifier.fillMaxWidth()) {
            Tab.entries.forEach { t ->
                val selected = t == tab && !showDebug
                Column(
                    Modifier
                        .weight(1f)
                        .clickable {
                            tab = t
                            showDebug = false
                        }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, contentDescription = null, tint = if (selected) Mint else TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(t.label, color = if (selected) Mint else TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}
