package com.myfamily.meow.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myfamily.meow.MainActivity
import com.myfamily.meow.R
import com.myfamily.meow.repository

class DailyReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val pending = applicationContext.repository.countPending()
        if (pending > 0) notify(pending)
        if (!inputData.getBoolean(KEY_ONE_OFF, false)) DailyReviewScheduler.schedule(applicationContext)
        return Result.success()
    }

    private fun notify(count: Int) {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "하루 소비 검토 알림", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("오늘 소비 ${count}건이 아직 기다리고 있어요 👀")
            .setContentText("오늘 하루 쓴 돈을 정리해볼까요?")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_ONE_OFF = "one_off"
        private const val CHANNEL_ID = "daily_review"
        private const val NOTIFICATION_ID = 1
    }
}
