package com.smartattendance.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foreground service that keeps the student informed while the app is closed.
 *
 * It polls `GET {apiBase}/api/student/notifications` with the restricted
 * device token (obtained from `POST /api/student/notifications/device-token`)
 * and shows a local notification for every new unread item. Seen ids are kept
 * in SharedPreferences so restarting the service does not re-notify.
 *
 * The service is started by [NotifyBridge.setSession] and stopped by
 * [NotifyBridge.clearSession] (sign-out). It never uses the user's login
 * token — only the restricted device token.
 */
class NotificationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            pollOnce()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        NotifyHelper.ensureChannels(this)
        startForeground(FOREGROUND_ID, NotifyHelper.foregroundNotification(this))
        handler.post(pollRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pollOnce() {
        val prefs = getSharedPreferences(NotifyBridge.PREFS, Context.MODE_PRIVATE)
        val apiBase = prefs.getString(NotifyBridge.KEY_API_BASE, null)?.trimEnd('/')
        val token = prefs.getString(NotifyBridge.KEY_DEVICE_TOKEN, null)
        if (apiBase.isNullOrEmpty() || token.isNullOrEmpty()) {
            stopSelf()
            return
        }
        Thread {
            try {
                val body = httpGet("$apiBase/api/student/notifications", token) ?: return@Thread
                val items = JSONObject(body).optJSONArray("items") ?: return@Thread
                val seen = prefs.getStringSet(KEY_SEEN, mutableSetOf())?.toMutableSet()
                    ?: mutableSetOf()
                var changed = false
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isEmpty()) continue
                    val read = item.optBoolean("read", false)
                    if (!read && !seen.contains(id)) {
                        NotifyHelper.show(
                            this,
                            id,
                            item.optString("title", "Smart Attendance"),
                            item.optString("body", "")
                        )
                        seen.add(id)
                        changed = true
                    }
                }
                if (changed) {
                    // Cap stored ids so preferences do not grow forever.
                    val trimmed = seen.takeLast(MAX_SEEN).toSet()
                    prefs.edit().putStringSet(KEY_SEEN, trimmed).apply()
                }
            } catch (_: Exception) {
                // Offline or server error: try again on the next tick.
            }
        }.start()
    }

    private fun httpGet(url: String, token: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val FOREGROUND_ID = 1001
        const val POLL_INTERVAL_MS = 30_000L
        const val MAX_SEEN = 200
        const val KEY_SEEN = "seen_ids"

        @Volatile var isRunning: Boolean = false
    }
}
