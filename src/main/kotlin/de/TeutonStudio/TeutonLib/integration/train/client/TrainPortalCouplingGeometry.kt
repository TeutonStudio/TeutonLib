package de.TeutonStudio.TeutonLib.integration.train.client

import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** Geometry only: coupling coordinates are never written back to the train or its bogeys. */
object TrainPortalCouplingGeometry {
    data class Plane(val point: Vec3, val normal: Vec3)
    data class Segment(val start: Vec3, val end: Vec3, val firstHead: Boolean, val secondHead: Boolean)

    /** A pending local portal copy must not hide the ready physical endpoint in the other world. */
    internal fun <T> preferredEndpoint(copies: Iterable<T>, ready: (T) -> Boolean, local: (T) -> Boolean): T? {
        var far: T? = null
        for (copy in copies) {
            if (!ready(copy)) continue
            if (local(copy)) return copy
            if (far == null) far = copy
        }
        return far
    }

    fun clip(first: Vec3, second: Vec3, planes: List<Plane>): Segment? {
        if (!finite(first) || !finite(second)) return null
        var from = 0.0
        var to = 1.0
        for (plane in planes) {
            val a = first.subtract(plane.point).dot(plane.normal)
            val b = second.subtract(plane.point).dot(plane.normal)
            if (a < 0.0 && b < 0.0) return null
            if ((a < 0.0) != (b < 0.0)) {
                val crossing = a / (a - b)
                if (a < 0.0) from = maxOf(from, crossing) else to = minOf(to, crossing)
            }
        }
        if (from >= to) return null
        val delta = second.subtract(first)
        return Segment(first.add(delta.scale(from)), first.add(delta.scale(to)), from == 0.0, to == 1.0)
    }

    fun crossesOpening(first: Vec3, second: Vec3, origin: Vec3, normal: Vec3,
        axisW: Vec3, axisH: Vec3, halfWidth: Double, halfHeight: Double): Boolean {
        val a = first.subtract(origin).dot(normal)
        val b = second.subtract(origin).dot(normal)
        if (a * b > 0.0 || abs(a - b) < 1.0e-9) return false
        val crossing = first.add(second.subtract(first).scale(a / (a - b))).subtract(origin)
        return abs(crossing.dot(axisW)) <= halfWidth + 0.02 &&
            abs(crossing.dot(axisH)) <= halfHeight + 0.02
    }

    fun plausible(first: Vec3, second: Vec3, maximumLength: Double): Boolean =
        finite(first) && finite(second) && maximumLength > 0.0 &&
            first.distanceToSqr(second) in 1.0e-8..(maximumLength * maximumLength)

    private fun finite(point: Vec3) = point.x.isFinite() && point.y.isFinite() && point.z.isFinite()
}
