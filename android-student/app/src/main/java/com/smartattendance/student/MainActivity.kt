package com.smartattendance.student

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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.IOException

/**
 * Native shell for the Smart Attendance lecturer web app.
 *
 * The web UI (built with Vite into `assets/`) is served from
 * `https://appassets.androidplatform.net/` so it keeps its exact design,
 * routing and localStorage behaviour. This activity only adds the native
 * bridges the web code expects (`window.AndroidBle`, `window.AndroidNotify`)
 * and runtime permissions. It does not add any UI of its own.
 */
class MainActivity : Activity() {

    lateinit var webView: WebView
        private set

    private lateinit var bleBridge: BleBridge
    private lateinit var notifyBridge: NotifyBridge

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

        webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setContentView(webView)

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
                if (url.scheme != "https" || url.host != ASSET_HOST) return null
                val path = url.path?.removePrefix("/") ?: return null
                if (path.isEmpty()) return serveIndex()
                return try {
                    val stream = assets.open(path)
                    WebResourceResponse(guessMime(path), charsetFor(path), stream)
                } catch (e: IOException) {
                    // SPA fallback: if the WebView restores a deep link such as
                    // /class after process death, serve index.html and let
                    // React Router handle the route.
                    if (request.isForMainFrame) serveIndex() else null
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                // Keep bundled assets and blob downloads inside the WebView.
                if (url.host == ASSET_HOST || url.scheme == "blob") return false
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
            // The web app saves files through the AndroidNotify.saveFile bridge.
            // This listener is a safety net for plain http(s) downloads only.
            if (url.startsWith("http://") || url.startsWith("https://")) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    // Ignore.
                }
            }
        }

        val btFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, btFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothStateReceiver, btFilter)
        }

        // Notification permission (Android 13+). The web app shows local
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

        webView.loadUrl("https://$ASSET_HOST/index.html")
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
        if (webView.canGoBack()) {
            webView.goBack()
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

    private fun serveIndex(): WebResourceResponse? {
        return try {
            WebResourceResponse("text/html", "UTF-8", assets.open("index.html"))
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
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val REQ_BLE = 1001
        const val REQ_NOTIFICATIONS = 1002
        const val REQ_STORAGE = 1003
    }
}
