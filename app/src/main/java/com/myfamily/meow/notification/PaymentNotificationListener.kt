package com.myfamily.meow.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.myfamily.meow.BuildConfig
import com.myfamily.meow.data.entity.ParseStatus
import com.myfamily.meow.data.entity.RawPaymentEvent
import com.myfamily.meow.debug.FakePaymentNotifier
import com.myfamily.meow.notification.parser.NotificationParser
import com.myfamily.meow.repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val isFake = BuildConfig.DEBUG && sbn.packageName == packageName
        if (!isFake && !PaymentSources.isSource(sbn.packageName)) return

        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        if (!PaymentSources.passesSenderFilter(sbn.packageName, title, text)) return
        if (!NotificationParser.looksLikePayment(title, text)) return

        val parsed = NotificationParser.parse(title, text)
        val label = if (isFake) {
            extras.getString(FakePaymentNotifier.EXTRA_SOURCE_LABEL) ?: "테스트"
        } else {
            PaymentSources.label(sbn.packageName, title)
        }
        val event = RawPaymentEvent(
            packageName = sbn.packageName,
            rawTitle = title,
            rawText = text,
            amount = parsed?.amount,
            merchant = parsed?.merchant,
            detectedAt = sbn.postTime,
            fingerprint = "${sbn.packageName}|$title|$text|${sbn.postTime / 60_000}",
            parseStatus = if (parsed != null) ParseStatus.SUCCESS else ParseStatus.FAILED,
        )
        scope.launch { applicationContext.repository.recordNotification(event, label) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
