package com.smartattendance.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

/**
 * Implements the `window.AndroidNotify` bridge expected by `src/shared/native.ts`:
 *
 * - `show(id, title, body): void` — local notification for a new item.
 * - `setSession(apiBase, token): void` — start background polling service.
 * - `clearSession(): void` — stop service and forget the session.
 * - `backgroundStatus(): string` — JSON `{"active":bool,"unrestricted":bool}`.
 * - `requestUnrestricted(): void` — ask for battery-optimisation exemption.
 * - `saveFile(name, mime, base64): string` — save a file to Downloads.
 *   Returns exactly `"ok"` on success (the web code throws otherwise).
 */
class NotifyBridge(private val activity: Activity) {

    @JavascriptInterface
    fun show(id: String, title: String, body: String) {
        try {
            NotifyHelper.show(activity, id, title, body)
        } catch (_: Exception) {
            // Notifications must never break the web app.
        }
    }

    @JavascriptInterface
    fun setSession(apiBase: String, token: String) {
        try {
            val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_API_BASE, apiBase.trimEnd('/'))
                .putString(KEY_DEVICE_TOKEN, token)
                .apply()
            val intent = Intent(activity, NotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(intent)
            } else {
                activity.startService(intent)
            }
        } catch (_: Exception) {
            // Offline or service start failed; retried next time the app opens.
        }
    }

    @JavascriptInterface
    fun clearSession() {
        try {
            activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .edit()
                .remove(KEY_API_BASE)
                .remove(KEY_DEVICE_TOKEN)
                .apply()
            activity.stopService(Intent(activity, NotificationService::class.java))
        } catch (_: Exception) {
            // Best effort.
        }
    }

    @JavascriptInterface
    fun backgroundStatus(): String {
        val active = NotificationService.isRunning
        val unrestricted = try {
            val pm = activity.getSystemService(Activity.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(activity.packageName)
        } catch (_: Exception) {
            false
        }
        return JSONObject()
            .put("active", active)
            .put("unrestricted", unrestricted)
            .toString()
    }

    @JavascriptInterface
    fun requestUnrestricted() {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        } catch (_: Exception) {
            // Some devices block this intent; ignore.
        }
    }

    @JavascriptInterface
    fun saveFile(name: String, mime: String, base64: String): String {
        return try {
            val bytes = Base64.getDecoder().decode(base64)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(name, mime, bytes)
            } else {
                saveLegacy(name, bytes)
            }
            "ok"
        } catch (e: Exception) {
            "Could not save file: ${e.message}"
        }
    }

    private fun saveViaMediaStore(name: String, mime: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = activity.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Could not open output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun saveLegacy(name: String, bytes: ByteArray) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        FileOutputStream(file).use { it.write(bytes) }
    }

    companion object {
        const val PREFS = "bg_session"
        const val KEY_API_BASE = "api_base"
        const val KEY_DEVICE_TOKEN = "device_token"
    }
}
