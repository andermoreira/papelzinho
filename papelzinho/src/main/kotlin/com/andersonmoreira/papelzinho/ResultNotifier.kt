package com.andersonmoreira.papelzinho

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class ResultNotifier(
    private val context: Context,
) {
    fun notify(state: SessionState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "send_result",
                context.getString(R.string.notifications_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val message =
            when (state) {
                SessionState.DONE -> R.string.notify_done
                SessionState.EXPIRED -> R.string.notify_expired
                else -> R.string.notify_abort
            }
        manager.notify(
            1,
            Notification
                .Builder(context, "send_result")
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(message))
                .setStyle(Notification.BigTextStyle().bigText(context.getString(message)))
                .setAutoCancel(true)
                .build(),
        )
    }
}
