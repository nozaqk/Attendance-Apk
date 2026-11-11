package com.smartattendance.app.data

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class BleStatus(
    val available: Boolean,
    val bluetoothOn: Boolean,
    val permissionsGranted: Boolean,
    val scanning: Boolean,
    val message: String? = null
)

data class BleSighting(val nodeId: String, val rssi: Int, val timestamp: Long)

/**
 * Native Bluetooth LE scanner for classroom ESP32 nodes.
 * Reports only real sightings (never simulated). Each sighting is POSTed to
 * `/api/attendance/ble-observation` (throttled per node) and the server's
 * verdict is exposed via [result].
 */
class BleScanner(
    private val context: Context,
    private val api: ApiClient,
    private val scope: CoroutineScope
) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter get() = manager?.adapter

    private val _status = MutableStateFlow(BleStatus(false, false, false, false))
    val status: StateFlow<BleStatus> = _status

    private val _last = MutableStateFlow<BleSighting?>(null)
    val last: StateFlow<BleSighting?> = _last

    private val _result = MutableStateFlow<VerifyResult?>(null)
    val result: StateFlow<VerifyResult?> = _result

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var scanning = false
    private var sessionId: String? = null
    private var minGapMs: Long = 20_000

    /**
     * Send at most one observation per node per [minGapMs]. Throttling per node
     * (not globally) matters in a room full of phones and earbuds: one other
     * device must never delay the report of the classroom node.
     */
    private val lastSentByNode = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val nodeId = try {
                result.scanRecord?.deviceName
                    ?: result.device?.name
                    ?: result.device?.address
                    ?: return
            } catch (_: SecurityException) {
                return
            }
            val now = System.currentTimeMillis()
            _last.value = BleSighting(nodeId, result.rssi, now)
            val last = lastSentByNode[nodeId] ?: 0L
            if (now - last < minGapMs) return
            lastSentByNode[nodeId] = now
            val rssi = result.rssi
            val currentSession = sessionId
            scope.launch {
                try {
                    val body = JSONObject()
                        .put("nodeId", nodeId)
                        .put("rssi", rssi)
                        .put("timestamp", now)
                    currentSession?.let { body.put("sessionId", it) }
                    val res = api.post("/api/attendance/ble-observation", body)
                    _result.value = VerifyResult.fromJson(res)
                    _error.value = null
                } catch (e: ApiException) {
                    _error.value = e.message
                    _result.value = null
                    // Back off briefly, then allow the next attempt.
                    lastSentByNode[nodeId] = now - minGapMs + 5_000
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            refreshStatus("Scan failed (code $errorCode). Try again.")
        }
    }

    fun start(sessionId: String?, minGapMs: Long) {
        // A different class (or a fresh panel) starts with a clean verdict so a
        // previous session's "verified" never leaks into the new one.
        if (this.sessionId != sessionId) {
            _result.value = null
            _error.value = null
            lastSentByNode.clear()
        }
        this.sessionId = sessionId
        this.minGapMs = minGapMs
        val adapter = adapter
        if (adapter == null) {
            refreshStatus("Bluetooth is not available on this device.")
            return
        }
        if (!hasPermission()) {
            refreshStatus("Bluetooth permission is needed.")
            return
        }
        if (!adapter.isEnabled) {
            refreshStatus("Bluetooth is turned off. Turn it on to verify attendance.")
            return
        }
        if (scanning) return
        try {
            val scanner = adapter.bluetoothLeScanner ?: run {
                refreshStatus("Bluetooth scanner is unavailable.")
                return
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, callback)
            scanning = true
            refreshStatus()
        } catch (_: SecurityException) {
            refreshStatus("Bluetooth permission is needed.")
        } catch (e: Exception) {
            refreshStatus("Could not start scan: ${e.message}")
        }
    }

    fun stop() {
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {
        }
        refreshStatus()
    }

    fun refreshStatus(message: String? = null) {
        val adapter = adapter
        val available = adapter != null
        val on = adapter?.isEnabled == true
        val granted = hasPermission()
        val msg = message ?: when {
            !available -> "Bluetooth is not available on this device."
            !granted -> "Bluetooth permission is needed."
            !on -> "Bluetooth is turned off. Turn it on to verify attendance."
            else -> null
        }
        _status.value = BleStatus(available, on, granted, scanning, msg)
    }

    private fun hasPermission(): Boolean =
        requiredPermissions().all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    companion object {
        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }
}
