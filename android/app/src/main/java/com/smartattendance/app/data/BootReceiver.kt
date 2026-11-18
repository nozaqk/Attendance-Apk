package com.smartattendance.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the notification poller after a reboot or an app update, so class alerts
 * keep arriving even if the student never reopens the app after restarting the phone.
 *
 * `NotificationService.ensureRunning` is a no-op unless a device token is stored
 * (i.e. a student signed in and background alerts were set up), so nothing runs for
 * signed-out users or for the lecturer portal.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_QUICKBOOT_POWERON -> NotificationService.ensureRunning(context)
        }
    }

    private companion object {
        /** Some OEMs (HTC/Xiaomi) send this instead of BOOT_COMPLETED. */
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
