package com.myfamily.meow.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.myfamily.meow.BuildConfig
import com.myfamily.meow.data.entity.NotificationLog
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
        val isSource = isFake || PaymentSources.isSource(sbn.packageName)
        val diagnostics = BuildConfig.DEBUG && Diagnostics.isEnabled(this)
        if (!isSource && !diagnostics) return

        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        val dropReason = when {
            !isSource -> "수집 대상 앱 아님"
            !PaymentSources.passesSenderFilter(sbn.packageName, title, text) -> "보낸 사람 필터에 걸림"
            !NotificationParser.looksLikePayment(title, text) -> "결제 키워드 없음"
            else -> null
        }
        val parsed = if (dropReason == null) NotificationParser.parse(title, text) else null
        // Income (입금/환불/승인취소) is not tracked; drop it instead of recording a fake expense.
        if (parsed?.isIncome == true) {
            if (diagnostics) {
                scope.launch {
                    applicationContext.repository.log(NotificationLog(0, sbn.packageName, title, text, sbn.postTime, "수입 알림이라 수집 안 함"))
                }
            }
            return
        }

        scope.launch {
            val repository = applicationContext.repository
            if (dropReason == null) {
                val label = if (isFake) {
                    extras.getString(FakePaymentNotifier.EXTRA_SOURCE_LABEL) ?: "테스트"
                } else {
                    PaymentSources.label(sbn.packageName, title)
                }
                repository.recordNotification(
                    RawPaymentEvent(
                        packageName = sbn.packageName,
                        rawTitle = title,
                        rawText = text,
                        amount = parsed?.amount,
                        merchant = parsed?.merchant,
                        detectedAt = sbn.postTime,
                        fingerprint = "${sbn.packageName}|$title|$text|${sbn.postTime / 60_000}",
                        parseStatus = if (parsed != null) ParseStatus.SUCCESS else ParseStatus.FAILED,
                    ),
                    sourceLabel = label,
                )
            }
            if (diagnostics && !isFake && Diagnostics.looksMoneyRelated(title, text)) {
                val decision = dropReason
                    ?: parsed?.let { "저장: ${it.amount}원 / ${it.merchant}" }
                    ?: "금액 파싱 실패"
                repository.log(NotificationLog(0, sbn.packageName, title, text, sbn.postTime, decision))
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
