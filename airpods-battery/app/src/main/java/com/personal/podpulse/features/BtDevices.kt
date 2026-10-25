package com.personal.podpulse.features

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.personal.podpulse.ble.PodScanner
import com.personal.podpulse.data.AppSettings

data class PairedDevice(val address: String, val name: String)

/** Knows which paired Bluetooth device is "my AirPods" and whether it's connected. */
@SuppressLint("MissingPermission") // guarded by canConnect()
object BtDevices {

    fun canConnect(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun looksLikePods(name: String?): Boolean =
        name != null && (name.contains("airpods", ignoreCase = true) || name.contains("beats", ignoreCase = true))

    /** Paired devices, AirPods-looking ones first. */
    fun paired(context: Context): List<PairedDevice> {
        if (!canConnect(context)) return emptyList()
        val devices = runCatching { PodScanner.adapter(context)?.bondedDevices }.getOrNull().orEmpty()
        return devices
            .map { PairedDevice(it.address, runCatching { it.name }.getOrNull() ?: it.address) }
            .sortedWith(compareBy({ !looksLikePods(it.name) }, { it.name.lowercase() }))
    }

    /** Picks the first paired AirPods automatically if the user hasn't chosen one. */
    fun autoSelect(context: Context) {
        if (AppSettings.deviceAddress(context) != null) return
        paired(context).firstOrNull { looksLikePods(it.name) }?.let {
            AppSettings.setDevice(context, it.address, it.name)
        }
    }

    fun isMine(context: Context, device: BluetoothDevice): Boolean {
        val saved = AppSettings.deviceAddress(context)
        if (saved != null) return device.address == saved
        val name = if (canConnect(context)) runCatching { device.name }.getOrNull() else null
        if (!looksLikePods(name)) return false
        AppSettings.setDevice(context, device.address, name!!)
        return true
    }

    /** Asynchronously checks whether my AirPods are connected for media right now. */
    fun queryConnected(context: Context, onResult: (Boolean) -> Unit) {
        val address = AppSettings.deviceAddress(context)
        val adapter = PodScanner.adapter(context)
        if (address == null || adapter == null || !adapter.isEnabled || !canConnect(context)) {
            onResult(false)
            return
        }
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val connected = runCatching {
                    proxy.connectedDevices.any { it.address == address }
                }.getOrDefault(false)
                adapter.closeProfileProxy(profile, proxy)
                onResult(connected)
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }
        if (!adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)) onResult(false)
    }
}
