package com.personal.podpulse.ble

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.personal.podpulse.PodPulseApp
import com.personal.podpulse.data.AppSettings
import com.personal.podpulse.data.LastSeen
import com.personal.podpulse.features.BtDevices
import com.personal.podpulse.features.LocationCapture
import com.personal.podpulse.features.PodService
import kotlinx.coroutines.launch

/**
 * - boot / app update / any Bluetooth connection: re-arm the background scan
 *   (the system drops it on reboot and Bluetooth restarts)
 * - my AirPods connect: start [PodService]
 * - my AirPods disconnect: stop it and remember where the phone was
 */
class BluetoothEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(
            intent,
            BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java,
        )
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                rearm(context)
                if (device != null && BtDevices.isMine(context, device)) PodService.start(context)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (device == null || !BtDevices.isMine(context, device)) return
                PodService.stop(context)
                LastSeen.markDisconnected(context, System.currentTimeMillis())
                if (!AppSettings.flag(context, AppSettings.REMEMBER_LOCATION)) return
                val pending = goAsync()
                PodPulseApp.scope(context).launch {
                    try {
                        LocationCapture.captureLastSeen(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> rearm(context)
        }
    }

    private fun rearm(context: Context) {
        if (AppSettings.backgroundEnabled(context)) PodScanner.armBackground(context)
    }
}
