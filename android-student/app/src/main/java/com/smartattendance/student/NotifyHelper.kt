package com.smartattendance.student

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Shared notification helpers used by both the JS bridge and the background
 * foreground service. Uses only framework APIs (no AndroidX).
 */
object NotifyHelper {
    const val CHANNEL_ATTENDANCE = "attendance"
    const val CHANNEL_BACKGROUND = "background"

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

    /** Shows a local notification for one web notification item. */
    fun show(context: Context, id: String, title: String, body: String) {
        ensureChannels(context)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ATTENDANCE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        notification.setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setOngoing(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.setChannelId(CHANNEL_ATTENDANCE)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Stable per-item id so re-showing the same item replaces it.
        manager.notify(id.hashCode(), notification.build())
    }

    /** Ongoing foreground-service notification (low importance, silent). */
    fun foregroundNotification(context: Context): Notification {
        ensureChannels(context)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_BACKGROUND)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        notification.setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("Smart Attendance")
            .setContentText("Checking for class updates…")
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.setChannelId(CHANNEL_BACKGROUND)
        }
        return notification.build()
    }
}
