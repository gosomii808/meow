package com.myfamily.meow.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.myfamily.meow.notification.NotificationAccess
import com.myfamily.meow.ui.common.BrandLockup
import com.myfamily.meow.ui.debug.DebugScreen
import com.myfamily.meow.ui.history.HistoryScreen
import com.myfamily.meow.ui.home.HomeScreen
import com.myfamily.meow.ui.onboarding.OnboardingScreen
import com.myfamily.meow.ui.report.GoalScreen
import com.myfamily.meow.ui.report.ReportScreen
import com.myfamily.meow.ui.review.ReviewScreen
import com.myfamily.meow.ui.settings.SettingsScreen
import com.myfamily.meow.ui.theme.Background
import com.myfamily.meow.ui.theme.dz

private const val PREFS = "meow_prefs"
// v2: the Figma onboarding (tutorial + signup) replaced the first one, so show it again once.
private const val KEY_ONBOARDED = "onboarding_done_v2"

private enum class Screen { HOME, SWIPE, HISTORY, REPORT, GOALS, SETTINGS, DEBUG }

@Composable
fun MeowApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (onboarded) Background else Color.White)
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
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var listenerOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        listenerOn = NotificationAccess.isGranted(context)
        onPauseOrDispose { }
    }
    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.GOALS) Screen.REPORT else Screen.HOME
    }
    val home = { screen = Screen.HOME }

    when (screen) {
        Screen.HOME -> HomeScreen(
            listenerOn = listenerOn,
            onStartSwipe = { screen = Screen.SWIPE },
            onHistory = { screen = Screen.HISTORY },
            onReport = { screen = Screen.REPORT },
            onSettings = { screen = Screen.SETTINGS },
            onDebug = { screen = Screen.DEBUG },
            onFixListener = { context.startActivity(NotificationAccess.fallbackIntent()) },
        )
        Screen.SWIPE -> ReviewScreen(onDone = home)
        Screen.HISTORY -> HistoryScreen(onHome = home)
        Screen.REPORT -> ReportScreen(onHome = home, onEditGoals = { screen = Screen.GOALS })
        Screen.GOALS -> GoalScreen(onBack = { screen = Screen.REPORT })
        Screen.SETTINGS -> WithHeader(home) { SettingsScreen() }
        Screen.DEBUG -> WithHeader(home) { DebugScreen() }
    }
}

/** Settings/DEV keep their own layouts under the brand header (tap it to go home). */
@Composable
private fun WithHeader(onHome: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Background)) {
        BrandLockup(Modifier.padding(start = 27.dz, top = 13.dz), onClick = onHome)
        content()
    }
}
