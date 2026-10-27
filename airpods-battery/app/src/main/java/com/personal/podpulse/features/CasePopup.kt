package com.personal.podpulse.features

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.personal.podpulse.MainActivity
import com.personal.podpulse.data.AppSettings
import com.personal.podpulse.data.PodRepository
import com.personal.podpulse.data.PodStatus
import com.personal.podpulse.ui.CasePopupCard
import com.personal.podpulse.ui.PodPulseTheme

/**
 * AirPods only broadcast while the case lid is open or they're out of the case,
 * so the first broadcast after a stretch of silence means "case just opened".
 * Then we draw the battery card as an overlay window (needs "Display over other
 * apps"), which unlike launching a screen isn't blocked in the background.
 */
object CasePopup {
    private const val SILENCE_MS = 45_000L
    private const val COOLDOWN_MS = 60_000L
    private const val NEAR_RSSI = -75
    private const val SHOW_MS = 10_000L

    private val main = Handler(Looper.getMainLooper())
    private var lastHeard = 0L
    private var lastShown = 0L
    private var view: View? = null
    private var owner: OverlayOwner? = null

    @Volatile
    private var showPending = false

    val isShowing: Boolean get() = showPending || view != null

    /** Call for every decoded packet, before it's submitted to the repository. */
    @Synchronized
    fun onPacket(context: Context, s: PodStatus) {
        if (s.rssi < NEAR_RSSI) return // too far away to be the case in your hand
        val previous = PodRepository.status.value
        if (previous != null && previous.modelId != s.modelId) return
        if (lastHeard == 0L) lastHeard = previous?.timestamp ?: 0L

        val silence = s.timestamp - lastHeard
        lastHeard = s.timestamp
        if (silence < SILENCE_MS || s.timestamp - lastShown < COOLDOWN_MS) return
        if (!AppSettings.flag(context, AppSettings.CASE_POPUP) || MainActivity.isVisible) return
        lastShown = s.timestamp

        val app = context.applicationContext
        if (Settings.canDrawOverlays(app)) {
            showPending = true
            main.post {
                try {
                    show(app)
                } finally {
                    showPending = false
                }
            }
        } else {
            Alerts.showConnected(app, s)
        }
    }

    private fun show(context: Context) {
        if (view != null) return
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val overlayOwner = OverlayOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setContent { PodPulseTheme { CasePopupCard(onDismiss = { dismiss(context) }) } }
            // Like iOS, tapping anywhere outside the card closes it.
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) dismiss(context)
                false
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            dimAmount = 0.3f
        }

        overlayOwner.resume()
        val added = runCatching { wm.addView(composeView, params) }.isSuccess
        if (!added) {
            overlayOwner.destroy()
            return
        }
        view = composeView
        owner = overlayOwner
        main.postDelayed({ dismiss(context) }, SHOW_MS)
    }

    private fun dismiss(context: Context) {
        val v = view ?: return
        view = null
        runCatching { context.getSystemService(WindowManager::class.java)?.removeView(v) }
        owner?.destroy()
        owner = null
    }

    /** Minimal lifecycle so Compose can run outside an activity. */
    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performAttach()
            controller.performRestore(null)
        }

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
