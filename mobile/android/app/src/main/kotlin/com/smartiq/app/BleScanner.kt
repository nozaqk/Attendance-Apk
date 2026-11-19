package com.smartiq.app

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Bluetooth LE scanning for the classroom ESP32 nodes.
 *
 * This is the only Bluetooth code in the product: Dart decides what to do with a
 * sighting (throttling, posting /api/attendance/ble-observation, showing the
 * server's verdict). Only real radio sightings are ever reported — there is no
 * simulation path.
 */
class BleScanner(private val context: Context, private val emit: (Map<String, Any?>) -> Unit) {

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter get() = manager?.adapter

    private var scanning = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val nodeId = try {
                result.scanRecord?.deviceName
                    ?: result.device?.name
                    ?: result.device?.address
            } catch (_: SecurityException) {
                null
            } ?: return
            emit(
                mapOf(
                    "type" to "sighting",
                    "nodeId" to nodeId,
                    "rssi" to result.rssi,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            emit(status("Scan failed (code $errorCode). Try again."))
        }
    }

    fun start() {
        val adapter = adapter
        if (adapter == null) {
            emit(status("Bluetooth is not available on this device."))
            return
        }
        if (!permissionsGranted()) {
            emit(status("Bluetooth permission is needed."))
            return
        }
        if (!adapter.isEnabled) {
            emit(status("Bluetooth is turned off. Turn it on to verify attendance."))
            return
        }
        if (scanning) {
            emit(status())
            return
        }
        try {
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                emit(status("Bluetooth scanner is unavailable."))
                return
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, callback)
            scanning = true
            emit(status())
        } catch (_: SecurityException) {
            emit(status("Bluetooth permission is needed."))
        } catch (e: Exception) {
            emit(status("Could not start scan: ${e.message}"))
        }
    }

    fun stop() {
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {
            // Adapter already gone: nothing to stop.
        }
        emit(status())
    }

    fun permissionsGranted(): Boolean = requiredPermissions().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun status(message: String? = null): Map<String, Any?> {
        val adapter = adapter
        val available = adapter != null
        val enabled = adapter?.isEnabled == true
        val granted = permissionsGranted()
        val text = message ?: when {
            !available -> "Bluetooth is not available on this device."
            !granted -> "Bluetooth permission is needed."
            !enabled -> "Bluetooth is turned off. Turn it on to verify attendance."
            else -> null
        }
        return mapOf(
            "type" to "status",
            "available" to available,
            "bluetoothOn" to enabled,
            "permissionsGranted" to granted,
            "scanning" to scanning,
            "message" to text
        )
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
