package com.myfamily.meow.debug

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.myfamily.meow.R

/**
 * Posts fake payment notifications from this app. In debug builds the listener accepts
 * this app's own notifications, so the whole pipeline can be exercised without paying.
 */
object FakePaymentNotifier {
    const val EXTRA_SOURCE_LABEL = "com.myfamily.meow.FAKE_SOURCE_LABEL"
    private const val CHANNEL_ID = "fake_payments"

    data class Fake(val sourceLabel: String, val title: String, val text: String)

    /** Spec §24 demo: one 10,000원 purchase shows up as three notifications. */
    val CHARGE_SCENARIO = listOf(
        Fake("카카오뱅크", "[카카오뱅크] 출금", "10,000원 출금 | 카카오페이"),
        Fake("카카오페이", "카카오페이", "10,000원 충전 완료"),
        Fake("카카오페이", "카카오페이", "GS25 역삼점 10,000원 결제 완료"),
    )

    val SINGLES = listOf(
        Fake("KB국민", "KB국민카드", "[Web발신]\nKB국민카드(1234)승인 홍*동 5,500원 일시불 10/02 08:32 스타벅스 강남점 누적123,400원"),
        Fake("신한카드", "신한카드", "신한카드(5678)승인 홍*동 12,000원(일시불)10/02 12:15 배달의민족 누적135,400원"),
        Fake("토스", "토스", "CU 성균관대점에서 3,200원 결제했어요"),
    )

    @SuppressLint("MissingPermission") // Caller checks POST_NOTIFICATIONS.
    fun post(context: Context, fakes: List<Fake>) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "테스트 결제 알림", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val base = (System.currentTimeMillis() % 100_000).toInt()
        fakes.forEachIndexed { i, fake ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(fake.title)
                .setContentText(fake.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(fake.text))
                .addExtras(Bundle().apply { putString(EXTRA_SOURCE_LABEL, fake.sourceLabel) })
                .setAutoCancel(true)
                .build()
            manager.notify(base + i, notification)
        }
    }
}
