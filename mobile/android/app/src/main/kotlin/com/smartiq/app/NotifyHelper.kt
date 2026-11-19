package com.smartiq.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Notification channels and builders shared by the Flutter bridge and the
 * background poller. Uses only framework APIs.
 */
object NotifyHelper {
    const val CHANNEL_ATTENDANCE = "attendance"
    const val CHANNEL_BACKGROUND = "background"

    /** Set on the intent that opens the app from a notification; the feed opens. */
    const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"

    private const val GROUP_ALERTS = "smartiq_class_alerts"
    private const val REQUEST_OPEN_APP = 4001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ATTENDANCE) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ATTENDANCE,
                    "Class updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        if (manager.getNotificationChannel(CHANNEL_BACKGROUND) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BACKGROUND,
                    "Background checks",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    /** Shows one class alert; tapping it opens the app on the notifications feed. */
    fun show(context: Context, id: String, title: String, body: String) {
        ensureChannels(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ATTENDANCE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setOngoing(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setGroup(GROUP_ALERTS)
            .setContentIntent(openAppIntent(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ATTENDANCE)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Stable per-item id so re-showing the same item replaces it.
        manager.notify(id.hashCode(), builder.build())
    }

    /** Ongoing foreground-service notification (low importance, silent). */
    fun foregroundNotification(context: Context): Notification {
        ensureChannels(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_BACKGROUND)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("SmartIQ")
            .setContentText("Checking for class updates…")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_BACKGROUND)
        }
        return builder.build()
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, REQUEST_OPEN_APP, intent, pendingFlags)
    }
}
