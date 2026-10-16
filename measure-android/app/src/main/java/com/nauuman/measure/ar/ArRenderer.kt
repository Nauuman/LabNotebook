package com.nauuman.measure.ar

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.Session
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renders the camera feed and drives [MeasureEngine] once per frame.
 * The measurement graphics themselves are drawn by the 2D overlay view.
 */
class ArRenderer(
    private val engine: MeasureEngine,
    private val displayRotation: () -> Int,
) : GLSurfaceView.Renderer {

    /** Set from the UI thread when a session is created / resumed. */
    @Volatile var session: Session? = null

    /** Receives a camera image (without overlay) plus the snapshot it matches. */
    fun interface CaptureCallback {
        fun onCaptured(bitmap: Bitmap, snapshot: ArSnapshot)
    }

    @Volatile private var pendingCapture: CaptureCallback? = null

    private val background = BackgroundRenderer()
    private var width = 0
    private var height = 0
    private var geometrySession: Session? = null
    private var geometryRotation = -1
    private var textureSession: Session? = null

    fun requestCapture(callback: CaptureCallback) {
        pendingCapture = callback
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
        // A fresh GL context means a fresh texture; re-bind it to the session.
        textureSession = null
        geometrySession = null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        this.width = width
        this.height = height
        geometrySession = null
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        if (width == 0 || height == 0) return

        if (textureSession !== s) {
            s.setCameraTextureName(background.textureId)
            textureSession = s
        }
        val rotation = displayRotation()
        if (geometrySession !== s || geometryRotation != rotation) {
            s.setDisplayGeometry(rotation, width, height)
            geometrySession = s
            geometryRotation = rotation
        }

        val frame = try {
            s.update()
        } catch (e: Exception) {
            // Session paused or camera momentarily unavailable.
            Log.w(TAG, "session.update failed", e)
            return
        }

        background.draw(frame)
        engine.onFrame(s, frame, width, height)

        pendingCapture?.let { callback ->
            pendingCapture = null
            val snapshot = engine.lastSnapshot ?: return@let
            callback.onCaptured(readPixels(), snapshot)
        }
    }

    private fun readPixels(): Bitmap {
        val buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        buf.rewind()
        val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(buf)
        // GL's origin is bottom-left; flip vertically.
        val m = android.graphics.Matrix().apply { preScale(1f, -1f) }
        val flipped = Bitmap.createBitmap(raw, 0, 0, width, height, m, false)
        raw.recycle()
        return flipped.copy(Bitmap.Config.ARGB_8888, true).also { flipped.recycle() }
    }

    companion object {
        private const val TAG = "ArRenderer"
    }
}
