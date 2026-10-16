package com.nauuman.measure.ar

import android.graphics.PointF
import kotlin.math.sqrt

/** Small immutable 3D vector used for world-space positions (meters). */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun distanceTo(o: Vec3) = (this - o).length()
    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-6f) this else this * (1f / l)
    }

    companion object {
        val UP = Vec3(0f, 1f, 0f)
        fun of(a: FloatArray) = Vec3(a[0], a[1], a[2])
    }
}

object Geometry {
    /** Area of a planar 3D polygon (Newell's method). */
    fun polygonArea(points: List<Vec3>): Float {
        if (points.size < 3) return 0f
        var sum = Vec3(0f, 0f, 0f)
        for (i in points.indices) {
            sum += points[i] cross points[(i + 1) % points.size]
        }
        return sum.length() / 2f
    }

    fun pathLength(points: List<Vec3>, closed: Boolean): Float {
        var total = 0f
        for (i in 0 until points.size - 1) total += points[i].distanceTo(points[i + 1])
        if (closed && points.size > 2) total += points.last().distanceTo(points.first())
        return total
    }

    fun centroid(points: List<Vec3>): Vec3 {
        var s = Vec3(0f, 0f, 0f)
        points.forEach { s += it }
        return s * (1f / points.size.coerceAtLeast(1))
    }
}

/**
 * Projects world-space points into view (pixel) coordinates using the
 * camera's combined view-projection matrix, with near-plane clipping so
 * lines that pass behind the camera are still drawn correctly.
 */
class Projector(private val viewProj: FloatArray, val width: Int, val height: Int) {

    private class Clip(val x: Float, val y: Float, val z: Float, val w: Float)

    private fun toClip(p: Vec3): Clip {
        val m = viewProj
        return Clip(
            m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12],
            m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13],
            m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14],
            m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15],
        )
    }

    private fun toScreen(c: Clip): PointF {
        val nx = c.x / c.w
        val ny = c.y / c.w
        return PointF((nx * 0.5f + 0.5f) * width, (1f - (ny * 0.5f + 0.5f)) * height)
    }

    private fun lerp(a: Clip, b: Clip, t: Float) = Clip(
        a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t,
        a.z + (b.z - a.z) * t, a.w + (b.w - a.w) * t,
    )

    /** Screen position, or null if the point is behind the camera. */
    fun project(p: Vec3): PointF? {
        val c = toClip(p)
        return if (c.w <= NEAR_W) null else toScreen(c)
    }

    /** Depth along the view direction (clip w), useful for sizing. */
    fun depth(p: Vec3): Float = toClip(p).w

    fun isOnScreen(pt: PointF, margin: Float = 0f) =
        pt.x >= -margin && pt.y >= -margin && pt.x <= width + margin && pt.y <= height + margin

    /** Clips the segment against the near plane and projects it. */
    fun projectSegment(a: Vec3, b: Vec3): Pair<PointF, PointF>? {
        var ca = toClip(a)
        var cb = toClip(b)
        if (ca.w <= NEAR_W && cb.w <= NEAR_W) return null
        if (ca.w <= NEAR_W) ca = lerp(ca, cb, (NEAR_W - ca.w) / (cb.w - ca.w))
        else if (cb.w <= NEAR_W) cb = lerp(cb, ca, (NEAR_W - cb.w) / (ca.w - cb.w))
        return toScreen(ca) to toScreen(cb)
    }

    /** Sutherland–Hodgman clip of a polygon against the near plane. */
    fun projectPolygon(points: List<Vec3>): List<PointF> {
        if (points.size < 3) return emptyList()
        val clips = points.map { toClip(it) }
        val out = ArrayList<Clip>(clips.size + 2)
        for (i in clips.indices) {
            val cur = clips[i]
            val next = clips[(i + 1) % clips.size]
            val curIn = cur.w > NEAR_W
            val nextIn = next.w > NEAR_W
            if (curIn) out += cur
            if (curIn != nextIn) out += lerp(cur, next, (NEAR_W - cur.w) / (next.w - cur.w))
        }
        return if (out.size < 3) emptyList() else out.map { toScreen(it) }
    }

    companion object {
        private const val NEAR_W = 0.02f
    }
}
