package com.personal.podpulse.ble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personal.podpulse.data.AirPodsParser
import com.personal.podpulse.data.PodRepository

/**
 * Two scanning modes:
 *  - live: a regular callback scan while the app is on screen (fast updates)
 *  - background: a PendingIntent scan owned by the system Bluetooth stack, which
 *    keeps delivering results to [ScanResultReceiver] even after the app is
 *    closed, without a persistent notification. It's hardware-filtered to
 *    AirPods packets, so the battery cost is small.
 */
@SuppressLint("MissingPermission") // every entry point checks Permissions first
object PodScanner {
    const val ACTION_SCAN_RESULT = "com.personal.podpulse.SCAN_RESULT"

    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isBluetoothOn(context: Context): Boolean = adapter(context)?.isEnabled == true

    private fun scanner(context: Context): BluetoothLeScanner? {
        if (!Permissions.hasForeground(context)) return null
        val adapter = adapter(context) ?: return null
        if (!adapter.isEnabled) return null
        return adapter.bluetoothLeScanner
    }

    /** Matches Apple manufacturer data starting with 07 19 (proximity pairing, 25 bytes). */
    private fun filters(): List<ScanFilter> {
        val data = ByteArray(27).apply {
            this[0] = AirPodsParser.PROXIMITY_PAIRING.toByte()
            this[1] = 25
        }
        val mask = ByteArray(27).apply {
            this[0] = -1
            this[1] = -1
        }
        return listOf(
            ScanFilter.Builder()
                .setManufacturerData(AirPodsParser.APPLE_COMPANY_ID, data, mask)
                .build(),
        )
    }

    private fun settings(mode: Int): ScanSettings = ScanSettings.Builder()
        .setScanMode(mode)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setReportDelay(0)
        .build()

    fun startLive(context: Context, callback: ScanCallback): Boolean {
        val scanner = scanner(context) ?: return false
        return runCatching {
            scanner.startScan(filters(), settings(ScanSettings.SCAN_MODE_LOW_LATENCY), callback)
        }.isSuccess
    }

    fun stopLive(context: Context, callback: ScanCallback) {
        val scanner = scanner(context) ?: return
        runCatching { scanner.stopScan(callback) }
    }

    fun armBackground(context: Context): Boolean {
        val scanner = scanner(context) ?: return false
        val intent = backgroundIntent(context)
        runCatching { scanner.stopScan(intent) }
        return runCatching {
            scanner.startScan(filters(), settings(ScanSettings.SCAN_MODE_LOW_POWER), intent) == 0
        }.getOrDefault(false)
    }

    fun disarmBackground(context: Context) {
        val scanner = scanner(context) ?: return
        runCatching { scanner.stopScan(backgroundIntent(context)) }
    }

    private fun backgroundIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScanResultReceiver::class.java).setAction(ACTION_SCAN_RESULT)
        // The system fills in the scan results, so the PendingIntent must be mutable.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 1, intent, flags)
    }

    /** Decodes one result and offers it to the repository. Returns true if the widget should refresh. */
    fun handle(context: Context, result: ScanResult): Boolean {
        val data = result.scanRecord?.getManufacturerSpecificData(AirPodsParser.APPLE_COMPANY_ID)
        val status = AirPodsParser.parse(data, result.rssi, System.currentTimeMillis()) ?: return false
        return PodRepository.submit(context, status)
    }
}
