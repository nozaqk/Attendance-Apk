package com.smartattendance.lecturer

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * Implements the `window.AndroidBle` bridge expected by `src/shared/ble.tsx`:
 *
 * - `startScan(): string`  -> JSON `{"ok":true}` or `{"ok":false,"needsPermission":true}`
 * - `stopScan(): void`
 * - `getStatus(): string`  -> JSON BleStatus
 * - `requestPermissions(): void`
 * - callbacks `window.onBleMeasurement({nodeId, rssi, timestamp})` and
 *   `window.onBleStatus(BleStatus)` (objects, not strings).
 *
 * Scanning uses the real [android.bluetooth.le.BluetoothLeScanner]. Measurements
 * are never simulated: only actual sightings of BLE devices are reported.
 * The classroom ESP32 node is identified by its advertised name when present,
 * otherwise by its Bluetooth address; the backend resolves the nodeId.
 */
class BleBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    private val bluetoothManager: BluetoothManager? =
        activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    @Volatile private var scanning = false
    private val lastSentByNode = mutableMapOf<String, Long>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val nodeId = try {
                val record = result.scanRecord
                record?.deviceName
                    ?: result.device?.name
                    ?: result.device?.address
                    ?: return
            } catch (_: SecurityException) {
                return
            }
            val rssi = result.rssi
            val now = System.currentTimeMillis()
            synchronized(lastSentByNode) {
                val last = lastSentByNode[nodeId] ?: 0L
                if (now - last < MIN_GAP_MS) return
                lastSentByNode[nodeId] = now
            }
            val payload = JSONObject()
                .put("nodeId", nodeId)
                .put("rssi", rssi)
                .put("timestamp", now)
                .toString()
            webView.post {
                webView.evaluateJavascript(
                    "window.onBleMeasurement && window.onBleMeasurement($payload)",
                    null
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            pushStatus("Scan failed (code $errorCode). Try again.")
        }
    }

    @JavascriptInterface
    fun getStatus(): String {
        return statusJson().toString()
    }

    @JavascriptInterface
    fun startScan(): String {
        val adapter = adapter
        if (adapter == null) {
            return JSONObject().put("ok", false).put("message", "Bluetooth is not available on this device.").toString()
        }
        if (!hasPermission()) {
            return JSONObject().put("ok", false).put("needsPermission", true).toString()
        }
        if (!adapter.isEnabled) {
            return JSONObject().put("ok", false).put("message", "Bluetooth is turned off. Turn it on to verify attendance.").toString()
        }
        if (scanning) {
            return JSONObject().put("ok", true).toString()
        }
        return try {
            val scanner = adapter.bluetoothLeScanner
                ?: return JSONObject().put("ok", false).put("message", "Bluetooth scanner is unavailable.").toString()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, scanCallback)
            scanning = true
            pushStatus()
            JSONObject().put("ok", true).toString()
        } catch (e: SecurityException) {
            JSONObject().put("ok", false).put("needsPermission", true).toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("message", "Could not start scan: ${e.message}").toString()
        }
    }

    @JavascriptInterface
    fun stopScan() {
        stopScanInternal()
        pushStatus()
    }

    @JavascriptInterface
    fun requestPermissions() {
        activity.requestBlePermissions()
    }

    fun stopScanInternal() {
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
            // Scanner may already be stopped.
        }
    }

    fun pushStatus(message: String? = null) {
        val json = statusJson(message).toString()
        webView.post {
            webView.evaluateJavascript(
                "window.onBleStatus && window.onBleStatus($json)",
                null
            )
        }
    }

    private fun statusJson(message: String? = null): JSONObject {
        val adapter = adapter
        val available = adapter != null
        val bluetoothOn = adapter?.isEnabled == true
        val granted = hasPermission()
        val obj = JSONObject()
            .put("available", available)
            .put("bluetoothOn", bluetoothOn)
            .put("permissionsGranted", granted)
            .put("scanning", scanning)
        val msg = message ?: when {
            !available -> "Bluetooth is not available on this device."
            !granted -> "Bluetooth permission is needed."
            !bluetoothOn -> "Bluetooth is turned off. Turn it on to verify attendance."
            else -> null
        }
        if (msg != null) obj.put("message", msg)
        return obj
    }

    private fun hasPermission(): Boolean {
        return requiredPermissions().all {
            activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val MIN_GAP_MS = 2_000L

        fun requiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
}
