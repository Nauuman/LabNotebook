package com.personal.podpulse.ble

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.personal.podpulse.PodPulseApp
import com.personal.podpulse.features.CasePopup
import com.personal.podpulse.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
        if (!refresh && !CasePopup.isShowing) return

        val pending = goAsync()
        PodPulseApp.scope(context).launch {
            try {
                if (refresh) WidgetUpdater.push(context)
                // Keep the process alive while the case pop-up is on screen.
                withTimeoutOrNull(9_000) {
                    while (CasePopup.isShowing) delay(250)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
