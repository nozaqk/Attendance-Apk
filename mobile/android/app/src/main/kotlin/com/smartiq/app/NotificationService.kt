package com.smartiq.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foreground service that keeps delivering class notifications while the app is
 * closed (swiped away, screen off, phone restarted).
 *
 * It polls `GET {apiBase}/api/student/notifications/feed` — the one route that
 * accepts the restricted `notify` device token — every 30 s and posts a local
 * notification for every new unread item. Seen ids live in SharedPreferences so a
 * restart (sticky restart, reboot) does not re-notify.
 *
 * The service is started on student sign-in / app start and stopped on sign-out.
 * It never uses the user's login token, only the restricted device token.
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
        // START_STICKY: if Android kills the service for memory, it is recreated and
        // polling resumes without the user reopening the app.
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pollOnce() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val apiBase = prefs.getString(KEY_API_BASE, null)?.trimEnd('/')
        val token = prefs.getString(KEY_DEVICE_TOKEN, null)
        if (apiBase.isNullOrEmpty() || token.isNullOrEmpty()) {
            stopSelf()
            return
        }
        Thread {
            try {
                val result = httpGet("$apiBase$FEED_PATH", token)
                if (result == null) return@Thread
                if (result.first == HttpURLConnection.HTTP_UNAUTHORIZED ||
                    result.first == HttpURLConnection.HTTP_FORBIDDEN
                ) {
                    // Device token expired or was revoked (sign-out, 90-day expiry):
                    // drop it so the app issues a fresh one next time it is opened.
                    prefs.edit().remove(KEY_DEVICE_TOKEN).apply()
                    handler.post { stopSelf() }
                    return@Thread
                }
                val items = JSONObject(result.second).optJSONArray("items") ?: return@Thread
                val seen = prefs.getStringSet(KEY_SEEN, mutableSetOf())?.toMutableSet()
                    ?: mutableSetOf()
                var changed = false
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isEmpty() || seen.contains(id)) continue
                    NotifyHelper.show(
                        this,
                        id,
                        item.optString("title", "SmartIQ"),
                        item.optString("body", "")
                    )
                    seen.add(id)
                    changed = true
                }
                if (changed) {
                    // Cap stored ids so preferences do not grow forever.
                    prefs.edit().putStringSet(KEY_SEEN, seen.toList().takeLast(MAX_SEEN).toSet()).apply()
                }
            } catch (_: Exception) {
                // Offline or server error: try again on the next tick.
            }
        }.start()
    }

    private fun httpGet(url: String, token: String): Pair<Int, String>? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            code to body
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val FOREGROUND_ID = 1001
        const val POLL_INTERVAL_MS = 30_000L
        const val MAX_SEEN = 200
        const val KEY_SEEN = "seen_ids"
        const val PREFS = "bg_session"
        const val KEY_API_BASE = "api_base"
        const val KEY_DEVICE_TOKEN = "device_token"

        /** Restricted-token feed; `/notifications` refuses `notify` scope tokens with 401. */
        private const val FEED_PATH = "/api/student/notifications/feed"

        @Volatile
        var isRunning: Boolean = false

        /** Starts the poller when a device token exists and it is not already running. */
        fun ensureRunning(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val apiBase = prefs.getString(KEY_API_BASE, null)
            val token = prefs.getString(KEY_DEVICE_TOKEN, null)
            if (apiBase.isNullOrEmpty() || token.isNullOrEmpty() || isRunning) return
            val intent = Intent(context, NotificationService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Foreground-service start refused (background restriction): the app
                // retries the next time it is opened, and the boot receiver retries
                // after a restart.
            }
        }

        /** Stops polling and forgets the device token (sign-out). */
        fun stopAndForget(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val apiBase = prefs.getString(KEY_API_BASE, null)?.trimEnd('/')
            val token = prefs.getString(KEY_DEVICE_TOKEN, null)
            prefs.edit().remove(KEY_DEVICE_TOKEN).remove(KEY_SEEN).apply()
            try {
                context.stopService(Intent(context, NotificationService::class.java))
            } catch (_: Exception) {
            }
            if (!apiBase.isNullOrEmpty() && !token.isNullOrEmpty()) {
                // Revoke the token server-side so a copied device cannot keep reading the feed.
                Thread {
                    try {
                        val connection = URL("$apiBase$FEED_PATH").openConnection() as HttpURLConnection
                        connection.requestMethod = "DELETE"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.connectTimeout = 10_000
                        connection.readTimeout = 10_000
                        connection.responseCode
                        connection.disconnect()
                    } catch (_: Exception) {
                    }
                }.start()
            }
        }
    }
}
