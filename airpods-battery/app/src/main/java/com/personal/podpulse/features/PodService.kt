package com.personal.podpulse.features

import android.app.Service
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.personal.podpulse.MainActivity
import com.personal.podpulse.ble.PodScanner
import com.personal.podpulse.data.AppSettings
import com.personal.podpulse.data.PodRepository
import com.personal.podpulse.ui.PopupActivity
import com.personal.podpulse.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs only while my AirPods are connected. Scans at full speed so ear
 * detection reacts quickly, shows the case pop-up, and keeps an ongoing
 * battery notification.
 */
class PodService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scanning = false
    private lateinit var audio: AudioManager

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::onResult)
    }

    private val earDetector = EarDetector(
        isPlaying = { audio.isMusicActive },
        pause = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE) },
        resume = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY) },
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        Alerts.ensureChannels(this)
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                Alerts.ID_LIVE,
                Alerts.liveNotification(this, PodRepository.snapshot(this)),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                },
            )
        }.isSuccess
        if (!started) {
            stopSelf()
            return
        }

        scanning = PodScanner.startFiltered(this, callback)
        observe()
    }

    private fun observe() {
        val startedAt = System.currentTimeMillis()
        var previous = PodRepository.status.value
        scope.launch {
            PodRepository.status.collect { s ->
                if (s == null) return@collect
                val prev = previous
                previous = s

                // A fresh reading after a gap means the case was just opened / buds connected.
                val gap = prev == null || s.timestamp - prev.timestamp > POPUP_GAP_MS
                if (gap && s.timestamp >= startedAt) showPopup()

                if (prev == null || !s.sameReadingAs(prev)) Alerts.updateLive(this@PodService, s)

                if (!s.model.single && AppSettings.flag(this@PodService, AppSettings.AUTO_PAUSE)) {
                    earDetector.update((if (s.leftInEar) 1 else 0) + (if (s.rightInEar) 1 else 0))
                }
            }
        }
    }

    private fun onResult(result: ScanResult) {
        if (PodScanner.handle(this, result)) {
            val app = applicationContext
            scope.launch { WidgetUpdater.push(app) }
        }
    }

    private fun showPopup() {
        if (!AppSettings.flag(this, AppSettings.CASE_POPUP) || MainActivity.isVisible) return
        val status = PodRepository.status.value ?: return
        if (Settings.canDrawOverlays(this)) {
            val launched = runCatching {
                startActivity(
                    Intent(this, PopupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            if (launched) return
        }
        Alerts.showConnected(this, status)
    }

    private fun sendMediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    override fun onDestroy() {
        if (scanning) PodScanner.stopLive(this, callback)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val POPUP_GAP_MS = 30_000L

        /** Only worth running when a feature that needs fast updates is on. */
        fun needed(context: Context): Boolean =
            AppSettings.flag(context, AppSettings.AUTO_PAUSE) ||
                AppSettings.flag(context, AppSettings.CASE_POPUP)

        fun start(context: Context) {
            if (!needed(context)) return
            // Throws on Android 12+ if we're in the background without an exemption
            // (exempt once battery optimisation is off for PodPulse).
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, PodService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PodService::class.java))
        }
    }
}
