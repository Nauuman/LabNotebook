package com.nauuman.measure.ar

import android.opengl.Matrix
import com.google.ar.core.Anchor
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.cos

enum class MeasureMode { LINE, PATH }

/** Immutable view of one measurement, safe to hand to the UI thread. */
class MeasurementSnap(
    val id: Long,
    val points: List<Vec3>,
    val pointIds: List<Long>,
    val closed: Boolean,
) {
    val segmentLengths: List<Float> = buildList {
        for (i in 0 until points.size - 1) add(points[i].distanceTo(points[i + 1]))
        if (closed && points.size > 2) add(points.last().distanceTo(points.first()))
    }
    val totalLength: Float = segmentLengths.sum()
    val area: Float = if (closed) Geometry.polygonArea(points) else 0f
}

class ReticleSnap(
    val position: Vec3,
    val normal: Vec3,
    /** Set when the reticle has snapped onto an existing point. */
    val snappedPointId: Long?,
    /** True when the live point was locked to the vertical guide. */
    val verticalGuide: Boolean,
)

/** Everything the overlay needs to draw one frame. */
class ArSnapshot(
    val width: Int,
    val height: Int,
    val viewProj: FloatArray,
    val trackingState: TrackingState,
    val failureReason: TrackingFailureReason,
    val hasFoundSurface: Boolean,
    val reticle: ReticleSnap?,
    val measurements: List<MeasurementSnap>,
    val activeId: Long?,
    val planes: List<List<Vec3>>,
) {
    val active: MeasurementSnap? get() = measurements.firstOrNull { it.id == activeId }

    /** Length of the segment currently following the reticle, if any. */
    val liveLength: Float?
        get() {
            val a = active ?: return null
            val r = reticle ?: return null
            return a.points.lastOrNull()?.distanceTo(r.position)
        }
}

/**
 * Owns all ARCore anchors and measurement state. Every method except [post]
 * must be called on the GL thread, because ARCore frames are only valid there.
 */
class MeasureEngine {

    sealed interface Action {
        data object AddPoint : Action
        data object Finish : Action
        data object Undo : Action
        data object Clear : Action
        data class Delete(val id: Long) : Action
    }

    enum class Event { POINT_ADDED, MEASUREMENT_DONE, NO_SURFACE, SNAPPED }

    interface Listener {
        fun onSnapshot(snapshot: ArSnapshot)
        fun onEvent(event: Event)
    }

    @Volatile var listener: Listener? = null
    @Volatile var mode: MeasureMode = MeasureMode.LINE
    @Volatile var snapEnabled = true
    @Volatile var showPlanes = true
    @Volatile var snapRadiusPx = 80f

    @Volatile var lastSnapshot: ArSnapshot? = null
        private set

    private val actions = ConcurrentLinkedQueue<Action>()

    private class MPoint(val id: Long, val anchor: Anchor) {
        fun position(): Vec3 = anchor.pose.let { Vec3(it.tx(), it.ty(), it.tz()) }
    }

    private class Measurement(val id: Long) {
        val points = mutableListOf<MPoint>()
        var closed = false
    }

    private sealed class Target(val position: Vec3, val normal: Vec3) {
        class Existing(val point: MPoint, position: Vec3, normal: Vec3) : Target(position, normal)
        class Hit(val hit: HitResult, position: Vec3, normal: Vec3) : Target(position, normal)
        class Guided(position: Vec3, normal: Vec3) : Target(position, normal)
    }

    private val measurements = mutableListOf<Measurement>()
    private var activeId: Long? = null
    private var nextId = 1L
    private var hasFoundSurface = false
    private var lastSnappedId: Long? = null

    fun post(action: Action) {
        actions.add(action)
    }

    private val active: Measurement? get() = measurements.firstOrNull { it.id == activeId }

    fun onFrame(session: Session, frame: Frame, width: Int, height: Int) {
        val camera = frame.camera
        val proj = FloatArray(16)
        val view = FloatArray(16)
        val viewProj = FloatArray(16)
        camera.getProjectionMatrix(proj, 0, 0.05f, 100f)
        camera.getViewMatrix(view, 0)
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)
        val projector = Projector(viewProj, width, height)

        val tracking = camera.trackingState
        val target = if (tracking == TrackingState.TRACKING) findTarget(frame, projector, width, height) else null

        val snapped = (target as? Target.Existing)?.point?.id
        if (snapped != null && snapped != lastSnappedId) listener?.onEvent(Event.SNAPPED)
        lastSnappedId = snapped

        while (true) {
            val action = actions.poll() ?: break
            handle(action, session, target)
        }

        val planes = if (showPlanes && tracking == TrackingState.TRACKING) collectPlanes(session) else emptyList()
        if (planes.isNotEmpty() || target != null) hasFoundSurface = true

        val snapshot = ArSnapshot(
            width = width,
            height = height,
            viewProj = viewProj,
            trackingState = tracking,
            failureReason = camera.trackingFailureReason,
            hasFoundSurface = hasFoundSurface,
            reticle = target?.let {
                ReticleSnap(it.position, it.normal, (it as? Target.Existing)?.point?.id, it is Target.Guided)
            },
            measurements = measurements.map { m ->
                MeasurementSnap(m.id, m.points.map { it.position() }, m.points.map { it.id }, m.closed)
            },
            activeId = activeId,
            planes = planes,
        )
        lastSnapshot = snapshot
        listener?.onSnapshot(snapshot)
    }

    // ---- Targeting -------------------------------------------------------

    private fun findTarget(frame: Frame, projector: Projector, width: Int, height: Int): Target? {
        val cx = width / 2f
        val cy = height / 2f

        // 1. Snap to an existing point near the center of the screen.
        if (snapEnabled) {
            val current = active
            var best: MPoint? = null
            var bestDist = snapRadiusPx
            for (p in allPoints()) {
                if (current != null && p in current.points) {
                    val closesLoop = p === current.points.first() && current.points.size >= 3
                    if (!closesLoop) continue
                }
                val s = projector.project(p.position()) ?: continue
                val d = kotlin.math.hypot(s.x - cx, s.y - cy)
                if (d < bestDist) {
                    bestDist = d
                    best = p
                }
            }
            if (best != null) {
                val pose = best.anchor.pose
                return Target.Existing(best, best.position(), Vec3.of(pose.yAxis))
            }
        }

        // 2. Hit test against the real world.
        val hit = frame.hitTest(cx, cy).firstOrNull { isUsableHit(it, frame) } ?: return null
        val pose = hit.hitPose
        val pos = Vec3(pose.tx(), pose.ty(), pose.tz())
        val normal = Vec3.of(pose.yAxis)

        // 3. Lock onto a vertical guide when the user is measuring roughly straight up/down.
        val last = active?.points?.lastOrNull()?.position()
        if (last != null) {
            val d = pos - last
            val len = d.length()
            if (len > 0.03f && abs(d dot Vec3.UP) / len > VERTICAL_SNAP_COS) {
                return Target.Guided(last + Vec3.UP * (d dot Vec3.UP), normal)
            }
        }
        return Target.Hit(hit, pos, normal)
    }

    private fun isUsableHit(hit: HitResult, frame: Frame): Boolean =
        when (val t = hit.trackable) {
            is Plane -> t.isPoseInPolygon(hit.hitPose) &&
                Plane.Type.HORIZONTAL_DOWNWARD_FACING != t.type &&
                distanceToPlane(hit.hitPose, frame.camera.pose) > 0
            is Point -> t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
            is DepthPoint -> true
            else -> false
        }

    private fun distanceToPlane(planePose: Pose, cameraPose: Pose): Float {
        val normal = planePose.yAxis
        val dx = cameraPose.tx() - planePose.tx()
        val dy = cameraPose.ty() - planePose.ty()
        val dz = cameraPose.tz() - planePose.tz()
        return dx * normal[0] + dy * normal[1] + dz * normal[2]
    }

    // ---- Actions ---------------------------------------------------------

    private fun handle(action: Action, session: Session, target: Target?) {
        when (action) {
            Action.AddPoint -> addPoint(session, target)
            Action.Finish -> finishActive()
            Action.Undo -> undo()
            Action.Clear -> {
                measurements.clear()
                activeId = null
                releaseUnreferenced()
            }
            is Action.Delete -> {
                measurements.removeAll { it.id == action.id }
                if (activeId == action.id) activeId = null
                releaseUnreferenced()
            }
        }
    }

    private fun addPoint(session: Session, target: Target?) {
        if (target == null) {
            listener?.onEvent(Event.NO_SURFACE)
            return
        }
        val point = try {
            when (target) {
                is Target.Existing -> target.point
                is Target.Hit -> MPoint(nextId++, target.hit.createAnchor())
                is Target.Guided -> MPoint(
                    nextId++,
                    session.createAnchor(Pose.makeTranslation(target.position.x, target.position.y, target.position.z)),
                )
            }
        } catch (e: Exception) {
            listener?.onEvent(Event.NO_SURFACE)
            return
        }
        ownedPoints += point

        val current = active
        if (current == null) {
            val m = Measurement(nextId++)
            m.points += point
            measurements += m
            activeId = m.id
            listener?.onEvent(Event.POINT_ADDED)
            return
        }

        if (point === current.points.last()) return
        if (current.points.size >= 3 && point === current.points.first()) {
            current.closed = true
            activeId = null
            listener?.onEvent(Event.MEASUREMENT_DONE)
            return
        }
        current.points += point
        if (mode == MeasureMode.LINE && current.points.size >= 2) {
            activeId = null
            listener?.onEvent(Event.MEASUREMENT_DONE)
        } else {
            listener?.onEvent(Event.POINT_ADDED)
        }
    }

    private fun finishActive() {
        val current = active ?: return
        if (current.points.size < 2) measurements.remove(current)
        activeId = null
        releaseUnreferenced()
    }

    private fun undo() {
        val current = active
        if (current != null) {
            current.points.removeAt(current.points.lastIndex)
            if (current.points.isEmpty()) {
                measurements.remove(current)
                activeId = null
            }
        } else if (measurements.isNotEmpty()) {
            measurements.removeAt(measurements.lastIndex)
        }
        releaseUnreferenced()
    }

    private fun allPoints(): Set<MPoint> = measurements.flatMapTo(LinkedHashSet()) { it.points }

    /** Every point whose anchor we created and have not yet detached. */
    private val ownedPoints = mutableSetOf<MPoint>()

    /** Detaches anchors no measurement uses any more. */
    private fun releaseUnreferenced() {
        val live = allPoints()
        val dead = ownedPoints - live
        dead.forEach { it.anchor.detach() }
        ownedPoints.removeAll(dead)
    }

    // ---- Planes ----------------------------------------------------------

    private fun collectPlanes(session: Session): List<List<Vec3>> {
        val result = ArrayList<List<Vec3>>()
        for (plane in session.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState != TrackingState.TRACKING || plane.subsumedBy != null) continue
            val poly = plane.polygon ?: continue
            val pose = plane.centerPose
            val pts = ArrayList<Vec3>(poly.limit() / 2)
            val local = FloatArray(3)
            poly.rewind()
            while (poly.remaining() >= 2) {
                local[0] = poly.get()
                local[1] = 0f
                local[2] = poly.get()
                pts += Vec3.of(pose.transformPoint(local))
            }
            if (pts.size >= 3) result += pts
            if (result.size >= MAX_PLANES) break
        }
        return result
    }

    companion object {
        private const val MAX_PLANES = 24
        private val VERTICAL_SNAP_COS = cos(Math.toRadians(4.0)).toFloat()
    }
}
