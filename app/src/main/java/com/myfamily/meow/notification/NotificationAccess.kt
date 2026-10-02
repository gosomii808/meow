package com.myfamily.meow.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Notification access can only be granted by the user in system settings. */
object NotificationAccess {
    fun isGranted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Opens this app's toggle directly where supported, otherwise the listener list. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, PaymentNotificationListener::class.java).flattenToString(),
        )

    fun fallbackIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
