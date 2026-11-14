package de.TeutonStudio.TeutonLib.integration.fluid

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Hydrostatic fluid in a rectangular tank, without game or rendering dependencies.
 *
 * The tank bounds and result vertices use the same local coordinate system. [solve]'s
 * `localGravityUp` is the local gradient of world height: the transpose of the local-to-world
 * linear transform multiplied by world up. For rotation alone this equals inverse rotation
 * applied to world up; with local scale followed by rotation, multiply that vector's components
 * by the scale as well. Translation does not affect it. The returned surface then becomes
 * horizontal after the tank transform. The plane position preserves the requested fraction
 * of the entire interior volume, including for upside-down tanks and nonuniform scale.
 *
 * Solving involves 48 scalar bisections and should be cached by bounds, gravity and fill.
 * The scalar volume function is analytical; no sampling, voxelization or mesh rebuilding is
 * performed during the search. Coordinates are centered for clipping so distant sublevel
 * plots do not degrade the calculation's precision.
 */
object TankFluidGeometry {
    data class Point(val x: Double, val y: Double, val z: Double) {
        operator fun plus(other: Point) = Point(x + other.x, y + other.y, z + other.z)
        operator fun minus(other: Point) = Point(x - other.x, y - other.y, z - other.z)
        operator fun times(scale: Double) = Point(x * scale, y * scale, z * scale)
        fun dot(other: Point): Double = x * other.x + y * other.y + z * other.z
        fun cross(other: Point) = Point(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )

        private fun finite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

        internal fun normalized(): Point {
            require(finite()) { "Tank gravity must be finite" }
            val scale = max(abs(x), max(abs(y), abs(z)))
            require(scale > 0.0) { "Tank gravity must not be zero" }
            val scaled = Point(x / scale, y / scale, z / scale)
            return scaled * (1.0 / sqrt(scaled.dot(scaled)))
        }
    }

    data class Bounds(val min: Point, val max: Point) {
        init {
            require(listOf(min.x, min.y, min.z, max.x, max.y, max.z).all(Double::isFinite)) {
                "Tank bounds must be finite"
            }
            require(max.x > min.x && max.y > min.y && max.z > min.z) {
                "Tank interior must have positive dimensions"
            }
            require((max.x - min.x).isFinite() && (max.y - min.y).isFinite() && (max.z - min.z).isFinite()) {
                "Tank dimensions must be finite"
            }
        }
    }

    /** A convex polygon with counterclockwise vertices as seen from outside the fluid. */
    data class Face(val vertices: List<Point>, val normal: Point, val freeSurface: Boolean)

    /**
     * Closed fluid boundary. The free surface satisfies `surfaceNormal.dot(p) == surfaceOffset`;
     * occupied space satisfies `surfaceNormal.dot(p) <= surfaceOffset`. For gas, the normal is
     * opposite local gravity up. At zero fill [faces] is empty; at full fill it contains exactly
     * the six tank faces, with no duplicate free-surface polygon. Triangulation is left to callers.
     */
    data class Mesh(val faces: List<Face>, val surfaceNormal: Point, val surfaceOffset: Double)

    /**
     * Clip [bounds] to a horizontal plane while preserving [fillFraction] of its volume.
     * Finite fractions outside 0..1 are clamped. [localGravityUp] may be non-unit, but must be
     * finite and nonzero. [lighterThanAir] fills from the opposite end without changing volume.
     */
    fun solve(bounds: Bounds, localGravityUp: Point, fillFraction: Double, lighterThanAir: Boolean = false): Mesh {
        require(fillFraction.isFinite()) { "Tank fill must be finite" }
        val normal = localGravityUp.normalized() * if (lighterThanAir) -1.0 else 1.0
        val size = bounds.max - bounds.min
        val center = bounds.min + size * 0.5
        val half = size * 0.5
        val fraction = fillFraction.coerceIn(0.0, 1.0)
        val widths = doubleArrayOf(abs(normal.x) * size.x, abs(normal.y) * size.y, abs(normal.z) * size.z)
            .filter { it > 0.0 }.sorted()
        val scale = widths.last()
        val scaledWidths = widths.map { it / scale }
        val span = scaledWidths.sum()
        val offset = when (fraction) {
            0.0 -> -span * 0.5 * scale
            1.0 -> span * 0.5 * scale
            0.5 -> 0.0
            else -> {
                val tail = if (fraction < 0.5) fraction else 1.0 - fraction
                var low = 0.0
                var high = span * 0.5
                repeat(48) {
                    val middle = (low + high) * 0.5
                    if (volumeFraction(middle, scaledWidths) < tail) low = middle else high = middle
                }
                val lowerOffset = ((low + high) * 0.5 - span * 0.5) * scale
                if (fraction < 0.5) lowerOffset else -lowerOffset
            }
        }
        val planeOffset = normal.dot(center) + offset
        if (fraction == 0.0) return Mesh(emptyList(), normal, planeOffset)

        val corners = arrayOf(
            Point(-half.x, -half.y, -half.z), Point(half.x, -half.y, -half.z),
            Point(-half.x, half.y, -half.z), Point(half.x, half.y, -half.z),
            Point(-half.x, -half.y, half.z), Point(half.x, -half.y, half.z),
            Point(-half.x, half.y, half.z), Point(half.x, half.y, half.z)
        )
        val boxFaces = listOf(
            Face(listOf(corners[0], corners[4], corners[6], corners[2]), Point(-1.0, 0.0, 0.0), false),
            Face(listOf(corners[1], corners[3], corners[7], corners[5]), Point(1.0, 0.0, 0.0), false),
            Face(listOf(corners[0], corners[1], corners[5], corners[4]), Point(0.0, -1.0, 0.0), false),
            Face(listOf(corners[2], corners[6], corners[7], corners[3]), Point(0.0, 1.0, 0.0), false),
            Face(listOf(corners[0], corners[2], corners[3], corners[1]), Point(0.0, 0.0, -1.0), false),
            Face(listOf(corners[4], corners[5], corners[7], corners[6]), Point(0.0, 0.0, 1.0), false)
        )
        if (fraction == 1.0) return Mesh(boxFaces.map { it.translated(center) }, normal, planeOffset)

        val cap = mutableListOf<Point>()
        val faces = boxFaces.mapNotNullTo(mutableListOf()) { face ->
            val clipped = mutableListOf<Point>()
            var previous = face.vertices.last()
            var previousDistance = normal.dot(previous) - offset
            for (current in face.vertices) {
                val currentDistance = normal.dot(current) - offset
                if ((previousDistance <= 0.0) != (currentDistance <= 0.0)) {
                    val crossing = previous + (current - previous) * (previousDistance / (previousDistance - currentDistance))
                    clipped.addDistinct(crossing, size)
                    cap.addDistinct(crossing, size)
                }
                if (currentDistance <= 0.0) {
                    clipped.addDistinct(current, size)
                    if (currentDistance == 0.0) cap.addDistinct(current, size)
                }
                previous = current
                previousDistance = currentDistance
            }
            if (clipped.size >= 3) Face(clipped, face.normal, false) else null
        }
        if (cap.size >= 3) {
            val capCenter = cap.reduce(Point::plus) * (1.0 / cap.size)
            val axis = when {
                abs(normal.x) <= abs(normal.y) && abs(normal.x) <= abs(normal.z) -> Point(1.0, 0.0, 0.0)
                abs(normal.y) <= abs(normal.z) -> Point(0.0, 1.0, 0.0)
                else -> Point(0.0, 0.0, 1.0)
            }
            val tangent = axis.cross(normal).normalized()
            val bitangent = normal.cross(tangent)
            cap.sortBy { point ->
                val relative = point - capCenter
                atan2(relative.dot(bitangent), relative.dot(tangent))
            }
            faces.add(Face(cap, normal, true))
        }
        return Mesh(faces.map { it.translated(center) }, normal, planeOffset)
    }

    /** CDF of the sum of 1..3 independent uniforms with sorted widths, evaluated in its lower half. */
    private fun volumeFraction(position: Double, widths: List<Double>): Double {
        if (position <= 0.0) return 0.0
        return when (widths.size) {
            1 -> position / widths[0]
            2 -> {
                val a = widths[0]
                val b = widths[1]
                if (position < a) (position / a) * (position / b) * 0.5
                else (position - a * 0.5) / b
            }
            else -> {
                val a = widths[0]
                val b = widths[1]
                val c = widths[2]
                (integratedTwoUniforms(position, a, b) - integratedTwoUniforms(position - c, a, b)) / c
            }
        }.coerceIn(0.0, 1.0)
    }

    /** Integral of the two-uniform CDF; factored terms avoid cancellation for almost-axis-aligned tanks. */
    private fun integratedTwoUniforms(position: Double, a: Double, b: Double): Double = when {
        position <= 0.0 -> 0.0
        position < a -> (position / a) * (position / b) * position / 6.0
        position < b -> {
            val shifted = position - a * 0.5
            (shifted / b) * shifted * 0.5 + (a / b) * a / 24.0
        }
        position < a + b -> {
            val remaining = a + b - position
            position - (a + b) * 0.5 + (remaining / a) * (remaining / b) * remaining / 6.0
        }
        else -> position - (a + b) * 0.5
    }

    private fun Face.translated(offset: Point) = copy(vertices = vertices.map { it + offset })

    private fun MutableList<Point>.addDistinct(point: Point, size: Point) {
        if (none { abs(it.x - point.x) <= size.x * 1e-13 && abs(it.y - point.y) <= size.y * 1e-13 &&
                    abs(it.z - point.z) <= size.z * 1e-13 }) add(point)
    }
}
