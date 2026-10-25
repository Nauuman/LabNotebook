package com.personal.podpulse.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.podpulse.data.AppSettings

/** Background scans are dropped on reboot or Bluetooth restarts; this puts them back. */
class RearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AppSettings.backgroundEnabled(context)) {
            PodScanner.armBackground(context)
        }
    }
}
