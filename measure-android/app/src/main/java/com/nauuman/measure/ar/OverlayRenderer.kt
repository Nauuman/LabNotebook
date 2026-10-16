package com.nauuman.measure.ar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import com.nauuman.measure.data.Units
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Draws measurement lines, points, labels, detected planes and the aiming
 * reticle on a plain [Canvas]. Used both for the live screen overlay and to
 * stamp the measurements onto captured photos.
 */
class OverlayRenderer(private val density: Float) {

    class LabelHit(val rect: RectF, val measurementId: Long)

    private fun dp(v: Float) = v * density

    private val planeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(34, 255, 255, 255)
    }
    private val planeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.argb(80, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }
    private val lineShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(5.5f)
        color = Color.argb(90, 0, 0, 0)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(3f)
    }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = YELLOW
        pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f)
    }
    private val areaFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(70, 255, 214, 10)
    }
    private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.argb(140, 0, 0, 0)
    }
    private val reticleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = Color.WHITE
        setShadowLayer(dp(2f), 0f, 0f, Color.argb(120, 0, 0, 0))
    }
    private val reticleIdle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.argb(110, 255, 255, 255)
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = dp(15f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    fun draw(
        canvas: Canvas,
        s: ArSnapshot,
        imperial: Boolean,
        selectedId: Long?,
        drawReticle: Boolean = true,
        hits: MutableList<LabelHit>? = null,
    ) {
        hits?.clear()
        if (s.trackingState != com.google.ar.core.TrackingState.TRACKING && drawReticle) {
            // Positions are unreliable while tracking is lost; keep the screen clean.
            drawIdleReticle(canvas, s)
            return
        }
        val proj = Projector(s.viewProj, s.width, s.height)

        for (plane in s.planes) drawPolygon(canvas, proj.projectPolygon(plane), planeFill, planeStroke)

        val reticle = s.reticle
        for (m in s.measurements) {
            val selected = m.id == selectedId
            val isActive = m.id == s.activeId
            line.color = if (selected) YELLOW else Color.WHITE
            dotFill.color = line.color

            if (m.closed) drawPolygon(canvas, proj.projectPolygon(m.points), areaFill, null)

            val pts = m.points
            val segCount = if (m.closed) pts.size else pts.size - 1
            for (i in 0 until segCount) {
                drawSegment(canvas, proj, pts[i], pts[(i + 1) % pts.size])
            }
            if (isActive && reticle != null && pts.isNotEmpty() && drawReticle) {
                drawSegment(canvas, proj, pts.last(), reticle.position)
            }
            for (p in pts) proj.project(p)?.let { drawDot(canvas, it) }

            // Labels after lines so they sit on top.
            val alwaysLabel = segCount == 1
            for (i in 0 until segCount) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                drawSegmentLabel(canvas, proj, a, b, Units.length(a.distanceTo(b), imperial), alwaysLabel, m.id, hits)
            }
            if (isActive && reticle != null && pts.isNotEmpty() && drawReticle) {
                val a = pts.last()
                drawSegmentLabel(canvas, proj, a, reticle.position, Units.length(a.distanceTo(reticle.position), imperial), true, m.id, null)
            }
            if (m.closed) {
                proj.project(Geometry.centroid(pts))?.let { c ->
                    drawPill(canvas, c.x, c.y, Units.area(m.area, imperial), YELLOW, m.id, hits)
                }
            }
        }

        if (!drawReticle) return
        if (reticle == null) {
            drawIdleReticle(canvas, s)
            return
        }
        val active = s.active
        if (reticle.verticalGuide && active != null && active.points.isNotEmpty()) {
            val base = active.points.last()
            proj.projectSegment(base - Vec3.UP * 3f, base + Vec3.UP * 3f)?.let { (a, b) ->
                canvas.drawLine(a.x, a.y, b.x, b.y, guide)
            }
        }
        if (reticle.snappedPointId != null) {
            proj.project(reticle.position)?.let {
                canvas.drawCircle(it.x, it.y, dp(16f), reticleRing)
                dotFill.color = Color.WHITE
                canvas.drawCircle(it.x, it.y, dp(6f), dotFill)
            }
        } else {
            drawSurfaceRing(canvas, proj, reticle)
        }
    }

    private fun drawSegment(canvas: Canvas, proj: Projector, a: Vec3, b: Vec3) {
        val (sa, sb) = proj.projectSegment(a, b) ?: return
        canvas.drawLine(sa.x, sa.y, sb.x, sb.y, lineShadow)
        canvas.drawLine(sa.x, sa.y, sb.x, sb.y, line)
    }

    private fun drawDot(canvas: Canvas, p: PointF) {
        canvas.drawCircle(p.x, p.y, dp(5f), dotFill)
        canvas.drawCircle(p.x, p.y, dp(5f), dotRing)
    }

    private fun drawSegmentLabel(
        canvas: Canvas, proj: Projector, a: Vec3, b: Vec3, label: String,
        always: Boolean, id: Long, hits: MutableList<LabelHit>?,
    ) {
        val seg = proj.projectSegment(a, b) ?: return
        val (sa, sb) = seg
        val mx = (sa.x + sb.x) / 2f
        val my = (sa.y + sb.y) / 2f
        if (!always) {
            val screenLen = hypot(sb.x - sa.x, sb.y - sa.y)
            if (screenLen < text.measureText(label) + dp(36f)) return
        }
        drawPill(canvas, mx, my, label, Color.WHITE, id, hits)
    }

    private fun drawPill(canvas: Canvas, x: Float, y: Float, label: String, bg: Int, id: Long, hits: MutableList<LabelHit>?) {
        if (x < -dp(60f) || y < -dp(30f) || x > canvas.width + dp(60f) || y > canvas.height + dp(30f)) return
        val w = text.measureText(label) + dp(20f)
        val h = dp(28f)
        val rect = RectF(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
        pill.color = bg
        pill.setShadowLayer(dp(3f), 0f, dp(1f), Color.argb(90, 0, 0, 0))
        canvas.drawRoundRect(rect, h / 2f, h / 2f, pill)
        val fm = text.fontMetrics
        canvas.drawText(label, x, y - (fm.ascent + fm.descent) / 2f, text)
        hits?.add(LabelHit(RectF(rect).apply { inset(-dp(8f), -dp(8f)) }, id))
    }

    private fun drawPolygon(canvas: Canvas, pts: List<PointF>, fill: Paint, stroke: Paint?) {
        if (pts.size < 3) return
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        path.close()
        canvas.drawPath(path, fill)
        if (stroke != null) canvas.drawPath(path, stroke)
    }

    /** A ring lying flat on the detected surface, like a spirit-level target. */
    private fun drawSurfaceRing(canvas: Canvas, proj: Projector, r: ReticleSnap) {
        val n = r.normal.normalized()
        val helper = if (abs(n.y) < 0.9f) Vec3.UP else Vec3(1f, 0f, 0f)
        val t1 = (n cross helper).normalized()
        val t2 = (n cross t1).normalized()
        val radius = 0.035f
        val path = Path()
        var started = false
        var ok = true
        for (i in 0..RING_STEPS) {
            val ang = 2.0 * PI * i / RING_STEPS
            val p = r.position + t1 * (radius * cos(ang).toFloat()) + t2 * (radius * sin(ang).toFloat())
            val s = proj.project(p)
            if (s == null) {
                ok = false
                break
            }
            if (started) {
                path.lineTo(s.x, s.y)
            } else {
                path.moveTo(s.x, s.y)
                started = true
            }
        }
        val center = proj.project(r.position) ?: return
        if (ok) canvas.drawPath(path, reticleRing)
        dotFill.color = Color.WHITE
        canvas.drawCircle(center.x, center.y, dp(3f), dotFill)
    }

    private fun drawIdleReticle(canvas: Canvas, s: ArSnapshot) {
        val cx = s.width / 2f
        val cy = s.height / 2f
        canvas.drawCircle(cx, cy, dp(22f), reticleIdle)
        dotFill.color = Color.argb(150, 255, 255, 255)
        canvas.drawCircle(cx, cy, dp(2.5f), dotFill)
    }

    companion object {
        val YELLOW = Color.rgb(255, 214, 10)
        private const val RING_STEPS = 40
    }
}
