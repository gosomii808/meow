package com.myfamily.meow.notification

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.myfamily.meow.classification.Category

/**
 * Payment-context helper (spec §FR-03): which app was in the foreground when a payment
 * notification arrived. Used only as a classification hint and a memo note, never as proof.
 * Requires the user to grant "사용 정보 접근" (Usage Access) in system settings.
 */
object ForegroundApp {

    /** Last app resumed in the recent window. Provided by the team; reads Android usage events. */
    fun getLastForegroundApp(context: Context): String? {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val now = System.currentTimeMillis()

        // 최근 10초 동안의 앱 사용 이벤트
        val events = usageStatsManager.queryEvents(
            now - 10_000,
            now
        )

        val event = UsageEvents.Event()

        var lastPackage: String? = null
        var lastTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp > lastTime) {
                    lastTime = event.timeStamp
                    lastPackage = event.packageName
                }
            }
        }

        return lastPackage
    }

    /**
     * The foreground app at payment time, for the memo — any app except this app and system
     * UI/launcher noise (so even 토스/카톡 등 결제 수단 앱도 메모에 남긴다). Null when usage
     * access isn't granted or nothing was open.
     */
    fun relevantApp(context: Context): String? {
        if (!isGranted(context)) return null
        val pkg = getLastForegroundApp(context) ?: return null
        if (pkg == context.packageName || pkg in NOISE) return null
        return pkg
    }

    /** Display name for a package: our known list first, then the installed app's own label. */
    fun label(context: Context, packageName: String): String =
        KNOWN[packageName]?.first ?: runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)

    /** Category for a known purchase app (step ② of the classification chain), or null. */
    fun category(packageName: String?): Category? = packageName?.let { KNOWN[it]?.second }

    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** System UI and home launchers mean "no real app was open" — skip only these from the memo. */
    private val NOISE = setOf(
        "com.android.systemui",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher",
    )

    /** Purchase apps → (display name, category). Packages verified on the test device. */
    private val KNOWN: Map<String, Pair<String, Category>> = mapOf(
        "com.starbucks.co" to ("스타벅스" to Category.CAFE),
        "com.sampleapp" to ("배달의민족" to Category.FOOD),
        "com.fineapp.yogiyo" to ("요기요" to Category.FOOD),
        "com.coupang.mobile" to ("쿠팡" to Category.SHOPPING),
        "com.musinsa.store" to ("무신사" to Category.SHOPPING),
        "com.croquis.zigzag" to ("지그재그" to Category.SHOPPING),
        "com.oliveyoung" to ("올리브영" to Category.SHOPPING),
        "co.kr.cgv.cjcgv" to ("CGV" to Category.CULTURE),
        "kr.co.lottecinema.lcm" to ("롯데시네마" to Category.CULTURE),
        "com.cultsotry.yanolja.nativeapp" to ("야놀자" to Category.TRAVEL),
        "com.kyobo.ebook.common.b2c" to ("교보문고" to Category.EDUCATION),
        "com.kakao.taxi" to ("카카오 T" to Category.TRANSPORT),
    )
}
