package com.smartiq.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

/**
 * The Android host for the Flutter app.
 *
 * The product itself (screens, navigation, state, API, polling cadence, PDF
 * content) is Dart. This activity only exposes the three OS-level capabilities
 * Dart cannot do alone — BLE scanning, the closed-app alert service and writing
 * a PDF into Downloads — plus the notification tap that opens the feed.
 */
class MainActivity : FlutterActivity() {

    private var channel: MethodChannel? = null
    private var bleEventSink: EventChannel.EventSink? = null
    private var scanner: BleScanner? = null

    /** Set when the app was launched/raised by tapping a class alert. */
    private var pendingOpenNotifications = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotifyHelper.ensureChannels(this)
        pendingOpenNotifications =
            intent?.getBooleanExtra(NotifyHelper.EXTRA_OPEN_NOTIFICATIONS, false) == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(NotifyHelper.EXTRA_OPEN_NOTIFICATIONS, false)) {
            pendingOpenNotifications = true
            channel?.invokeMethod("openNotifications", null)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        val ble = BleScanner(this) { event ->
            runOnUiThread { bleEventSink?.success(event) }
        }
        scanner = ble

        EventChannel(messenger, BLE_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    bleEventSink = events
                    events?.success(ble.status())
                }

                override fun onCancel(arguments: Any?) {
                    bleEventSink = null
                }
            }
        )

        channel = MethodChannel(messenger, CHANNEL)
        channel?.setMethodCallHandler { call: MethodCall, result: MethodChannel.Result ->
            handle(call, result, ble)
        }
    }

    private fun handle(
        call: MethodCall,
        result: MethodChannel.Result,
        ble: BleScanner
    ) {
        when (call.method) {
            "ensureChannels" -> {
                NotifyHelper.ensureChannels(this)
                result.success(null)
            }

            "requestNotificationPermission" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_NOTIFICATIONS
                    )
                }
                result.success(null)
            }

            "requestBlePermissions" -> {
                requestPermissions(BleScanner.requiredPermissions(), REQUEST_BLE)
                result.success(null)
            }

            "enableBackgroundAlerts" -> {
                val apiBase = call.argument<String>("apiBase")?.trimEnd('/')
                val token = call.argument<String>("deviceToken")
                if (apiBase.isNullOrEmpty() || token.isNullOrEmpty()) {
                    result.success(null)
                    return
                }
                getSharedPreferences(NotificationService.PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(NotificationService.KEY_API_BASE, apiBase)
                    .putString(NotificationService.KEY_DEVICE_TOKEN, token)
                    .apply()
                NotificationService.ensureRunning(this)
                result.success(null)
            }

            "disableBackgroundAlerts" -> {
                NotificationService.stopAndForget(this)
                result.success(null)
            }

            "backgroundAlertsRunning" -> result.success(NotificationService.isRunning)

            "seenIds" -> {
                val seen = getSharedPreferences(NotificationService.PREFS, MODE_PRIVATE)
                    .getStringSet(NotificationService.KEY_SEEN, emptySet())
                    ?: emptySet()
                result.success(seen.toList())
            }

            "markSeen" -> {
                val ids = call.argument<List<String>>("ids") ?: emptyList()
                val prefs = getSharedPreferences(NotificationService.PREFS, MODE_PRIVATE)
                val seen = prefs.getStringSet(NotificationService.KEY_SEEN, emptySet())
                    ?.toMutableSet() ?: mutableSetOf()
                seen.addAll(ids)
                prefs.edit()
                    .putStringSet(
                        NotificationService.KEY_SEEN,
                        seen.toList().takeLast(NotificationService.MAX_SEEN).toSet()
                    )
                    .apply()
                result.success(null)
            }

            "showNotification" -> {
                val id = call.argument<String>("id") ?: ""
                if (id.isNotEmpty()) {
                    NotifyHelper.show(
                        this,
                        id,
                        call.argument<String>("title") ?: "SmartIQ",
                        call.argument<String>("body") ?: ""
                    )
                }
                result.success(null)
            }

            "consumeOpenNotifications" -> {
                val opened = pendingOpenNotifications
                pendingOpenNotifications = false
                result.success(opened)
            }

            "bleStatus" -> result.success(ble.status())

            "bleStart" -> {
                ble.start()
                result.success(null)
            }

            "bleStop" -> {
                ble.stop()
                result.success(null)
            }

            "exportActivityPdf" -> {
                val args = call.arguments
                if (args !is Map<*, *>) {
                    result.error("BAD_ARGS", "Missing export payload", null)
                    return
                }
                val payload = JSONObject(args)
                Thread {
                    try {
                        val fileName = PdfWriter.write(this, payload)
                        runOnUiThread { result.success(fileName) }
                    } catch (e: Exception) {
                        runOnUiThread {
                            result.error("PDF_FAILED", e.message ?: "Could not write the PDF", null)
                        }
                    }
                }.start()
            }

            else -> result.notImplemented()
        }
    }

    companion object {
        private const val CHANNEL = "com.smartiq.app/native"
        private const val BLE_CHANNEL = "com.smartiq.app/ble"
        private const val REQUEST_NOTIFICATIONS = 4002
        private const val REQUEST_BLE = 4003
        private const val REQUEST_STORAGE = 4004
    }
}
