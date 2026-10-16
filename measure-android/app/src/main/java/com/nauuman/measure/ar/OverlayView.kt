package com.nauuman.measure.ar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** Transparent view on top of the camera that draws [OverlayRenderer]. */
class OverlayView(context: Context) : View(context) {

    private val renderer = OverlayRenderer(resources.displayMetrics.density)
    private val hits = ArrayList<OverlayRenderer.LabelHit>()

    @Volatile private var snapshot: ArSnapshot? = null
    var imperial = false
    var selectedId: Long? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Called with the tapped measurement's id, or null when tapping empty space. */
    var onLabelTap: ((Long?) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f

    /** May be called from any thread. */
    fun update(s: ArSnapshot) {
        snapshot = s
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val s = snapshot ?: return
        renderer.draw(canvas, s, imperial, selectedId, drawReticle = true, hits = hits)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (hypot(event.x - downX, event.y - downY) < 24f * resources.displayMetrics.density) {
                    val hit = hits.lastOrNull { it.rect.contains(event.x, event.y) }
                    onLabelTap?.invoke(hit?.measurementId)
                }
            }
        }
        return true
    }

    /** Stamps the measurements onto a captured camera frame. */
    fun composeCapture(bitmap: android.graphics.Bitmap, s: ArSnapshot) {
        renderer.draw(Canvas(bitmap), s, imperial, selectedId = null, drawReticle = false)
    }
}
