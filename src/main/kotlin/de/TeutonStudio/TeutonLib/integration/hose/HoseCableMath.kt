package de.TeutonStudio.TeutonLib.integration.hose

import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/** World-space cable geometry, shared by the physics adapter and persistence validation. */
object HoseCableMath {
    const val MAX_POINTS = 1027
    const val SEGMENT_LENGTH = 1.0
    const val RADIUS = 2.05 / 16.0
    const val EXIT = RADIUS + .02
    const val DOCK_STUB = 1.0/16.0 + RADIUS + .02
    fun length(points: List<Vec3>): Double = points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
    fun valid(points: List<Vec3>): Boolean = points.size in 2..MAX_POINTS &&
        points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }

    /** Match material positions along two polylines even when contact nodes are inserted or
     * removed. The target's corners stay intact; matching raw indices makes the whole hose jump. */
    fun matchTopology(source: List<Vec3>, target: List<Vec3>): List<Vec3> {
        require(source.size >= 2 && target.size >= 2)
        fun distances(points: List<Vec3>): DoubleArray {
            val result = DoubleArray(points.size)
            for (index in 1..points.lastIndex) result[index] = result[index-1] + points[index-1].distanceTo(points[index])
            return result
        }
        val from = distances(source)
        val to = distances(target)
        var edge = 0
        return target.indices.map { index ->
            when (index) {
                0 -> source.first()
                target.lastIndex -> source.last()
                else -> {
                    val fraction = if (to.last() > 1e-12) to[index]/to.last() else index.toDouble()/target.lastIndex
                    val distance = from.last()*fraction
                    while (edge < source.lastIndex-1 && from[edge+1] < distance) edge++
                    val span = from[edge+1]-from[edge]
                    source[edge].lerp(source[edge+1], if (span > 1e-12) ((distance-from[edge])/span).coerceIn(0.0,1.0) else 0.0)
                }
            }
        }
    }

    fun startTangent(points: List<Vec3>): Vec3 = points.zipWithNext().firstOrNull { (a, b) -> a.distanceToSqr(b) > 1e-12 }
        ?.let { (a, b) -> b.subtract(a).normalize() } ?: Vec3.ZERO
    fun endTangent(points: List<Vec3>): Vec3 = points.zipWithNext().lastOrNull { (a, b) -> a.distanceToSqr(b) > 1e-12 }
        ?.let { (a, b) -> a.subtract(b).normalize() } ?: Vec3.ZERO

    /** The prefix already spent around obstacles cannot be borrowed by the final leg. */
    fun limitEnd(points: List<Vec3>, target: Vec3, length: Double): Vec3 {
        if (points.size < 2) return target
        val previous = points[points.lastIndex - 1]
        val available = (length - length(points.dropLast(1))).coerceAtLeast(0.0)
        val delta = target.subtract(previous)
        return if (delta.lengthSqr() <= available * available) target else previous.add(delta.normalize().scale(available))
    }

    fun segmentCount(length: Double) = ceil(length.coerceIn(.001, 256.35) / SEGMENT_LENGTH).toInt().coerceIn(1, MAX_POINTS-1)
}
