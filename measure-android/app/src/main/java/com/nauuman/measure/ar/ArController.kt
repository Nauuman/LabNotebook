package com.nauuman.measure.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** The small amount of per-frame state the Compose UI cares about. */
data class Hud(
    val message: String? = null,
    val liveLength: Float? = null,
    val lastLength: Float? = null,
    val lastArea: Float? = null,
    val activeId: Long? = null,
    val activePoints: Int = 0,
    val measurementCount: Int = 0,
    val canAdd: Boolean = false,
)

/**
 * Owns the ARCore [Session], the camera [GLSurfaceView] and the overlay.
 * The session only runs while the activity is resumed, the Measure tab is
 * visible and the camera permission is granted.
 */
class ArController(private val activity: Activity) {

    sealed interface Status {
        data object Idle : Status
        data object Running : Status
        data class Error(val message: String) : Status
    }

    val engine = MeasureEngine()
    val overlay = OverlayView(activity)
    val surfaceView = GLSurfaceView(activity)

    @Suppress("DEPRECATION")
    private val renderer = ArRenderer(engine) { activity.windowManager.defaultDisplay.rotation }

    var status by mutableStateOf<Status>(Status.Idle)
        private set

    private val _hud = MutableStateFlow(Hud())
    val hud: StateFlow<Hud> = _hud

    private val _events = MutableSharedFlow<MeasureEngine.Event>(extraBufferCapacity = 16)
    val events: SharedFlow<MeasureEngine.Event> = _events

    var hapticsEnabled = true

    private var session: Session? = null
    private var installRequested = false
    private var activityResumed = false
    private var visible = false
    private var running = false

    init {
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(renderer)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        engine.snapRadiusPx = 40f * activity.resources.displayMetrics.density
        engine.listener = object : MeasureEngine.Listener {
            override fun onSnapshot(snapshot: ArSnapshot) {
                overlay.update(snapshot)
                _hud.value = buildHud(snapshot)
            }

            override fun onEvent(event: MeasureEngine.Event) {
                _events.tryEmit(event)
                if (!hapticsEnabled) return
                val effect = when (event) {
                    MeasureEngine.Event.POINT_ADDED, MeasureEngine.Event.MEASUREMENT_DONE -> HapticFeedbackConstants.VIRTUAL_KEY
                    MeasureEngine.Event.SNAPPED -> HapticFeedbackConstants.CLOCK_TICK
                    MeasureEngine.Event.NO_SURFACE -> HapticFeedbackConstants.LONG_PRESS
                }
                surfaceView.post { surfaceView.performHapticFeedback(effect) }
            }
        }
    }

    fun onActivityResumed(resumed: Boolean) {
        activityResumed = resumed
        refresh()
    }

    fun setVisible(v: Boolean) {
        visible = v
        refresh()
    }

    /** Re-evaluates whether the session should run (e.g. after a permission grant). */
    fun refresh() {
        val permitted = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val shouldRun = activityResumed && visible && permitted
        if (shouldRun && !running) start() else if (!shouldRun && running) stop()
    }

    fun retry() {
        status = Status.Idle
        installRequested = false
        refresh()
    }

    private fun start() {
        if (session == null) {
            val s = try {
                val install = ArCoreApk.getInstance().requestInstall(activity, !installRequested)
                if (install == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    // Play Store install flow; we'll be resumed afterwards.
                    installRequested = true
                    return
                }
                Session(activity).also { configure(it) }
            } catch (e: UnavailableDeviceNotCompatibleException) {
                fail("This phone doesn't support Google Play Services for AR, which measuring needs. The Level tool still works.")
                return
            } catch (e: UnavailableUserDeclinedInstallationException) {
                fail("Google Play Services for AR is required to measure. Tap Retry to install it.")
                return
            } catch (e: UnavailableArcoreNotInstalledException) {
                fail("Please install Google Play Services for AR from the Play Store.")
                return
            } catch (e: UnavailableApkTooOldException) {
                fail("Please update Google Play Services for AR from the Play Store.")
                return
            } catch (e: UnavailableSdkTooOldException) {
                fail("This app needs an update to work with your version of ARCore.")
                return
            } catch (e: Exception) {
                fail("Couldn't start AR: ${e.message ?: e.javaClass.simpleName}")
                return
            }
            session = s
            renderer.session = s
        }

        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            fail("The camera is in use by another app. Close it and tap Retry.")
            return
        } catch (e: Exception) {
            fail("Couldn't start the camera: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        surfaceView.onResume()
        running = true
        status = Status.Running
    }

    private fun configure(s: Session) {
        val config = Config(s).apply {
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            focusMode = Config.FocusMode.AUTO
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            lightEstimationMode = Config.LightEstimationMode.DISABLED
            if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                // Depth lets hit tests land on non-flat objects too.
                depthMode = Config.DepthMode.AUTOMATIC
            }
        }
        s.configure(config)
    }

    private fun fail(message: String) {
        status = Status.Error(message)
    }

    private fun stop() {
        surfaceView.onPause()
        session?.pause()
        running = false
    }

    fun destroy() {
        if (running) stop()
        renderer.session = null
        session?.close()
        session = null
    }

    fun post(action: MeasureEngine.Action) = engine.post(action)

    /** Captures the camera frame with measurements drawn on top; [onResult] runs on the main thread. */
    fun capture(onResult: (Bitmap, ArSnapshot) -> Unit) {
        if (!running) return
        renderer.requestCapture { bitmap, snapshot ->
            activity.runOnUiThread {
                overlay.composeCapture(bitmap, snapshot)
                onResult(bitmap, snapshot)
            }
        }
    }

    private fun buildHud(s: ArSnapshot): Hud {
        val active = s.active
        val done = s.measurements.lastOrNull { it.id != s.activeId }
        return Hud(
            message = coachingMessage(s),
            liveLength = s.liveLength?.let { (it * 1000f).roundToInt() / 1000f },
            lastLength = done?.let { (it.totalLength * 1000f).roundToInt() / 1000f },
            lastArea = done?.takeIf { it.closed }?.let { (it.area * 10000f).roundToInt() / 10000f },
            activeId = s.activeId,
            activePoints = active?.points?.size ?: 0,
            measurementCount = s.measurements.size,
            canAdd = s.trackingState == TrackingState.TRACKING && s.reticle != null,
        )
    }

    private fun coachingMessage(s: ArSnapshot): String? {
        if (s.trackingState != TrackingState.TRACKING) {
            return when (s.failureReason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> "More light needed"
                TrackingFailureReason.EXCESSIVE_MOTION -> "Move your phone more slowly"
                TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at a surface with more detail"
                TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
                TrackingFailureReason.BAD_STATE -> "AR is having trouble — try restarting"
                else -> "Move your phone around to start"
            }
        }
        if (!s.hasFoundSurface) return "Move your phone slowly to find a surface"
        if (s.reticle == null) return "Aim the dot at a surface"
        if (s.measurements.isEmpty()) return "Aim the dot at a starting point and tap +"
        return null
    }
}
