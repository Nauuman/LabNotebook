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
import com.personal.podpulse.features.Alerts
import com.personal.podpulse.features.CasePopup
import com.personal.podpulse.features.FindSignal

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

    /**
     * Matches any Apple manufacturer data whose first byte is 0x07 (proximity
     * pairing). Only the type byte is matched so that packets of any length get
     * through; a background scan needs a filter to keep running with the screen off.
     */
    private fun filters(): List<ScanFilter> = listOf(
        ScanFilter.Builder()
            .setManufacturerData(
                AirPodsParser.APPLE_COMPANY_ID,
                byteArrayOf(AirPodsParser.PROXIMITY_PAIRING.toByte()),
                byteArrayOf(-1),
            )
            .build(),
    )

    private fun settings(mode: Int): ScanSettings = ScanSettings.Builder()
        .setScanMode(mode)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setReportDelay(0)
        .build()

    /**
     * The live scan is unfiltered (we filter in code instead): some phones'
     * Bluetooth chips drop packets with hardware manufacturer-data filters.
     */
    fun startLive(context: Context, callback: ScanCallback): Boolean {
        val scanner = scanner(context)
        if (scanner == null) {
            ScanDiagnostics.setLiveScan("Unavailable (permission or Bluetooth)")
            return false
        }
        val result = runCatching {
            scanner.startScan(emptyList(), settings(ScanSettings.SCAN_MODE_LOW_LATENCY), callback)
        }
        ScanDiagnostics.setLiveScan(
            result.exceptionOrNull()?.let { "Failed: ${it.message}" } ?: "Running",
        )
        return result.isSuccess
    }

    /** Filtered full-speed scan, which (unlike an unfiltered one) keeps running with the screen off. */
    fun startFiltered(context: Context, callback: ScanCallback): Boolean {
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
            scanner.startScan(filters(), settings(ScanSettings.SCAN_MODE_BALANCED), intent) == 0
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
        ScanDiagnostics.onPacket()
        val data = result.scanRecord?.getManufacturerSpecificData(AirPodsParser.APPLE_COMPANY_ID)
            ?: return false
        ScanDiagnostics.onApplePacket(data)
        if (data.isEmpty() || data[0].toInt() != AirPodsParser.PROXIMITY_PAIRING) return false
        val status = AirPodsParser.parse(data, result.rssi, System.currentTimeMillis())
        ScanDiagnostics.onPodPacket(
            data,
            result.rssi,
            status?.let { "L ${it.left ?: "-"} R ${it.right ?: "-"} C ${it.case ?: "-"} " +
                "ear ${if (it.leftInEar) "L" else ""}${if (it.rightInEar) "R" else ""} " +
                "case ${if (it.leftInCase) "L" else ""}${if (it.rightInCase) "R" else ""}" },
        )
        if (status == null) return false
        FindSignal.offer(status)
        CasePopup.onPacket(context, status)
        if (!PodRepository.submit(context, status)) return false
        // Alert on the smoothed reading, never on a single packet.
        PodRepository.status.value?.let { Alerts.checkLowBattery(context, it) }
        return true
    }
}
