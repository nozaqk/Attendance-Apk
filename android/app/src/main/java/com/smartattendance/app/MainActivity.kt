package com.smartattendance.app

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import java.io.IOException

/**
 * Single-APK shell for Smart Attendance.
 *
 * On launch the user picks a role (Lecturer or Student). Each role loads its
 * own unchanged web app from bundled assets under a separate origin:
 *
 * - Lecturer -> https://lecturer.appassets.androidplatform.net/  (assets/lecturer/)
 * - Student  -> https://student.appassets.androidplatform.net/   (assets/student/)
 *
 * Separate origins mean separate localStorage: logins, tokens and data stay
 * fully separate, and each portal operates independently. The native bridges
 * (`window.AndroidBle`, `window.AndroidNotify`) are shared, but only the
 * student portal ever starts background notification polling.
 *
 * Pressing Back at a portal's root returns to the role picker so the user
 * can switch roles without reinstalling.
 */
class MainActivity : Activity() {

    private enum class Role { LECTURER, STUDENT }

    private lateinit var webView: WebView
    private lateinit var rolePicker: View
    private lateinit var bleBridge: BleBridge
    private lateinit var notifyBridge: NotifyBridge

    private var currentRole: Role? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                bleBridge.pushStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotifyHelper.ensureChannels(this)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        rolePicker = findViewById(R.id.rolePicker)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        // WebView debugging only in debug builds.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        bleBridge = BleBridge(this, webView)
        notifyBridge = NotifyBridge(this)
        webView.addJavascriptInterface(bleBridge, "AndroidBle")
        webView.addJavascriptInterface(notifyBridge, "AndroidNotify")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url
                val prefix = assetPrefixFor(url.host) ?: return null
                val path = url.path?.removePrefix("/") ?: return null
                if (path.isEmpty()) return serveIndex(prefix)
                return try {
                    val stream = assets.open(prefix + path)
                    WebResourceResponse(guessMime(path), charsetFor(path), stream)
                } catch (e: IOException) {
                    // SPA fallback: if the WebView restores a deep link such as
                    // /class after process death, serve index.html and let
                    // React Router handle the route.
                    if (request.isForMainFrame) serveIndex(prefix) else null
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                // Keep bundled portals and blob downloads inside the WebView.
                if (assetPrefixFor(url.host) != null || url.scheme == "blob") return false
                // Anything else (external links) opens in the browser.
                if (url.scheme == "http" || url.scheme == "https") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (_: Exception) {
                        // No browser available; ignore.
                    }
                    return true
                }
                return false
            }
        }

        webView.setDownloadListener { url, _, _, _, _ ->
            // The web apps save files through the AndroidNotify.saveFile bridge.
            // This listener is a safety net for plain http(s) downloads only.
            if (url.startsWith("http://") || url.startsWith("https://")) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    // Ignore.
                }
            }
        }

        findViewById<Button>(R.id.btnLecturer).setOnClickListener { selectRole(Role.LECTURER) }
        findViewById<Button>(R.id.btnStudent).setOnClickListener { selectRole(Role.STUDENT) }

        val btFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, btFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothStateReceiver, btFilter)
        }

        // Notification permission (Android 13+). The web apps show local
        // notifications through AndroidNotify.show; without this permission
        // they would be silently dropped.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATIONS
            )
        }
        // Legacy file saving needs storage permission on Android 8-9.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQ_STORAGE
            )
        }

        // Restore the selected role after process death / recreation.
        val saved = savedInstanceState?.getString(KEY_ROLE)
        if (saved != null) {
            selectRole(Role.valueOf(saved))
        } else {
            showPicker()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentRole?.let { outState.putString(KEY_ROLE, it.name) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BLE) {
            bleBridge.pushStatus()
        }
    }

    /** Called by [BleBridge.requestPermissions] (JS bridge thread). */
    fun requestBlePermissions() {
        runOnUiThread {
            requestPermissions(BleBridge.requiredPermissions(), REQ_BLE)
        }
    }

    override fun onBackPressed() {
        if (webView.visibility == View.VISIBLE) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                // At a portal's root: back to the role picker to switch roles.
                showPicker()
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (_: Exception) {
            // Already unregistered.
        }
        try {
            bleBridge.stopScanInternal()
        } catch (_: Exception) {
            // Best effort.
        }
        try {
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (_: Exception) {
            // Best effort.
        }
        super.onDestroy()
    }

    private fun selectRole(role: Role) {
        rolePicker.visibility = View.GONE
        webView.visibility = View.VISIBLE
        if (currentRole != role) {
            currentRole = role
            webView.loadUrl(urlFor(role))
            // Drop the other portal's history so Back goes to the picker,
            // not into the previous portal.
            webView.clearHistory()
        }
    }

    private fun showPicker() {
        webView.visibility = View.GONE
        rolePicker.visibility = View.VISIBLE
    }

    private fun urlFor(role: Role): String {
        return when (role) {
            Role.LECTURER -> "https://$HOST_LECTURER/index.html"
            Role.STUDENT -> "https://$HOST_STUDENT/index.html"
        }
    }

    private fun assetPrefixFor(host: String?): String? {
        return when (host) {
            HOST_LECTURER -> "lecturer/"
            HOST_STUDENT -> "student/"
            else -> null
        }
    }

    private fun serveIndex(prefix: String): WebResourceResponse? {
        return try {
            WebResourceResponse("text/html", "UTF-8", assets.open(prefix + "index.html"))
        } catch (e: IOException) {
            null
        }
    }

    private fun charsetFor(path: String): String? {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".html") || lower.endsWith(".htm") ||
                lower.endsWith(".js") || lower.endsWith(".css") ||
                lower.endsWith(".svg") || lower.endsWith(".json") ||
                lower.endsWith(".txt") -> "UTF-8"
            else -> null
        }
    }

    private fun guessMime(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
            lower.endsWith(".js") || lower.endsWith(".mjs") -> "text/javascript"
            lower.endsWith(".css") -> "text/css"
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".ico") -> "image/x-icon"
            lower.endsWith(".woff") -> "font/woff"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".ttf") -> "font/ttf"
            lower.endsWith(".map") -> "application/json"
            else -> "application/octet-stream"
        }
    }

    companion object {
        const val HOST_LECTURER = "lecturer.appassets.androidplatform.net"
        const val HOST_STUDENT = "student.appassets.androidplatform.net"
        const val REQ_BLE = 1001
        const val REQ_NOTIFICATIONS = 1002
        const val REQ_STORAGE = 1003
        const val KEY_ROLE = "selected_role"
    }
}
