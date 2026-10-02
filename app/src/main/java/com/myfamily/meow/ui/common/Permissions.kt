package com.myfamily.meow.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

fun canPostNotifications(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Returns a function that asks for POST_NOTIFICATIONS (minSdk 26 < 33, so it may already be implied). */
@Composable
fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): (Context) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return { context ->
        if (canPostNotifications(context)) onResult(true) else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
