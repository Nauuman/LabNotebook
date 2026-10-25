package com.personal.podpulse.ble

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.personal.podpulse.PodPulseApp
import com.personal.podpulse.widget.WidgetUpdater
import kotlinx.coroutines.launch

class ScanResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val results = IntentCompat.getParcelableArrayListExtra(
            intent,
            BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
            ScanResult::class.java,
        ) ?: return

        var refresh = false
        for (result in results) {
            if (PodScanner.handle(context, result)) refresh = true
        }
        if (!refresh) return

        val pending = goAsync()
        PodPulseApp.scope(context).launch {
            try {
                WidgetUpdater.push(context)
            } finally {
                pending.finish()
            }
        }
    }
}
