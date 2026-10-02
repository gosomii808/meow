package com.myfamily.meow.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * One-shot work chained day to day, so a time change applies immediately. WorkManager is
 * inexact (minutes late is fine per spec §17) and survives reboots without extra permissions.
 */
object DailyReviewScheduler {
    private const val WORK_NAME = "daily_review"

    fun schedule(context: Context) {
        val settings = ReminderSettings(context)
        val workManager = WorkManager.getInstance(context)
        if (!settings.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<DailyReviewWorker>()
            .setInitialDelay(delayUntil(settings.time).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Debug: fire the reminder now without touching the daily schedule. */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailyReviewWorker>()
            .setInputData(workDataOf(DailyReviewWorker.KEY_ONE_OFF to true))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun delayUntil(time: LocalTime, now: LocalDateTime = LocalDateTime.now()): Duration {
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
