package com.myfamily.meow.reminder

import android.content.Context
import java.time.LocalTime

/** Daily review reminder time (spec §17, UserSettings.reviewHour/Minute/notificationEnabled). */
class ReminderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var time: LocalTime
        get() = LocalTime.of(prefs.getInt(KEY_HOUR, 22), prefs.getInt(KEY_MINUTE, 0))
        set(value) = prefs.edit().putInt(KEY_HOUR, value.hour).putInt(KEY_MINUTE, value.minute).apply()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
    }
}
