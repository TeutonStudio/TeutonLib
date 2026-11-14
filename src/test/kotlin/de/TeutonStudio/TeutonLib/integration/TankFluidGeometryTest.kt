package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.integration.fluid.TankFluidGeometry
import de.TeutonStudio.TeutonLib.integration.fluid.TankFluidGeometry.Bounds
import de.TeutonStudio.TeutonLib.integration.fluid.TankFluidGeometry.Mesh
import de.TeutonStudio.TeutonLib.integration.fluid.TankFluidGeometry.Point
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TankFluidGeometryTest {
    private val bounds = Bounds(Point(-1.25, 0.125, -0.875), Point(2.75, 7.625, 1.375))

    @Test fun `upright and inverted fluid levels have the requested volume`() {
        for (up in listOf(Point(0.0, 1.0, 0.0), Point(0.0, -1.0, 0.0))) {
            for (fill in listOf(0.01, 0.25, 0.5, 0.99)) {
                val mesh = solveAndCheck(bounds, up, fill)
                val surface = mesh.faces.single { it.freeSurface }
                val expectedY = if (up.y > 0.0) bounds.min.y + (bounds.max.y - bounds.min.y) * fill
                    else bounds.max.y - (bounds.max.y - bounds.min.y) * fill
                surface.vertices.forEach { assertEquals(expectedY, it.y, 1e-11) }
            }
        }
    }

    @Test fun `sideways tanks use their actual horizontal width for fill height`() {
        for (up in listOf(Point(1.0, 0.0, 0.0), Point(-1.0, 0.0, 0.0), Point(0.0, 0.0, 1.0))) {
            for (fill in listOf(0.01, 0.5, 0.99)) solveAndCheck(bounds, up, fill)
        }
    }

    @Test fun `diagonal clipping remains closed when the surface crosses box vertices and edges`() {
        val cube = Bounds(Point(-1.0, -1.0, -1.0), Point(1.0, 1.0, 1.0))
        for (up in listOf(Point(1.0, 1.0, 0.0), Point(1.0, 1.0, 1.0), Point(-1.0, 1.0, 0.0))) {
            for (fill in listOf(0.01, 1.0 / 6.0, 0.5, 5.0 / 6.0, 0.99)) {
                solveAndCheck(cube, up, fill)
            }
        }
    }

    @Test fun `empty and full interiors do not add duplicate free surfaces`() {
        val up = Point(0.4, -0.8, 0.2)
        assertTrue(TankFluidGeometry.solve(bounds, up, 0.0).faces.isEmpty())
        assertTrue(TankFluidGeometry.solve(bounds, up, -0.25).faces.isEmpty())
        val full = TankFluidGeometry.solve(bounds, up, 1.0)
        assertEquals(6, full.faces.size)
        assertTrue(full.faces.none { it.freeSurface })
        assertEquals(boxVolume(bounds), signedVolume(full), 1e-10)
        assertEquals(full, TankFluidGeometry.solve(bounds, up, 1.25))
    }

    @Test fun `random rotations preserve volume and put the surface horizontal in world space`() {
        val random = Random(0x54414e4b)
        repeat(64) {
            val angles = Point(random.nextDouble(-PI, PI), random.nextDouble(-PI, PI), random.nextDouble(-PI, PI))
            // R^-1 * world-up is the Y row of R; calculate it independently from the solver.
            val localUp = Point(rotate(Point(1.0, 0.0, 0.0), angles).y,
                rotate(Point(0.0, 1.0, 0.0), angles).y, rotate(Point(0.0, 0.0, 1.0), angles).y)
            for (fill in listOf(0.01, 0.12345, 0.5, 0.99)) {
                val mesh = solveAndCheck(bounds, localUp, fill)
                val surface = mesh.faces.single { it.freeSurface }
                val worldNormal = rotate(surface.normal, angles)
                assertEquals(0.0, worldNormal.x, 1e-12)
                assertEquals(1.0, worldNormal.y, 1e-12)
                assertEquals(0.0, worldNormal.z, 1e-12)
                val height = rotate(surface.vertices.first(), angles).y
                surface.vertices.forEach { assertEquals(height, rotate(it, angles).y, 1e-11) }
            }
        }
    }

    @Test fun `nearly aligned rotations avoid cancellation in the volume solution`() {
        for (small in listOf(1e-15, 1e-10, 1e-6, 1e-3)) {
            for (up in listOf(Point(small, 1.0, small), Point(small, 1.0, 0.0), Point(1.0, small, -small))) {
                for (fill in listOf(0.01, 0.5, 0.99)) solveAndCheck(bounds, up, fill)
            }
        }
    }

    @Test fun `high aspect ratio and upside down tall tanks preserve small fill fractions`() {
        val tall = Bounds(Point(0.125, 0.125, 0.125), Point(2.875, 8.875, 2.875))
        val thin = Bounds(Point(-1500.0, -0.000003, -4.5), Point(1500.0, 0.000003, 4.5))
        for (box in listOf(tall, thin)) {
            for (up in listOf(Point(1e-15, -1.0, 1e-15), Point(1e-10, 1.0, 1e-15), Point(1.0, 1e-15, 0.0))) {
                for (fill in listOf(0.01, 0.06736, 0.5, 0.99)) solveAndCheck(box, up, fill, 1e-9)
            }
        }
    }

    @Test fun `nonuniform scale is included in the local world height gradient`() {
        val angles = Point(0.72, -1.83, 0.44)
        val scale = Point(2.0, 0.3, 1.5)
        val localUp = Point(rotate(Point(scale.x, 0.0, 0.0), angles).y,
            rotate(Point(0.0, scale.y, 0.0), angles).y, rotate(Point(0.0, 0.0, scale.z), angles).y)
        for (fill in listOf(0.01, 0.06736, 0.5, 0.99)) {
            val mesh = solveAndCheck(bounds, localUp, fill)
            val surface = mesh.faces.single { it.freeSurface }
            val heights = surface.vertices.map { rotate(Point(it.x * scale.x, it.y * scale.y, it.z * scale.z), angles).y }
            heights.forEach { assertEquals(heights.first(), it, 1e-11) }
        }
    }

    @Test fun `noncubic and distant sublevel interiors retain the same fluid volume`() {
        val offset = Point(30_000_000.0, -800_000.0, -20_000_000.0)
        val shifted = Bounds(bounds.min + offset, bounds.max + offset)
        for (fill in listOf(0.01, 0.5, 0.99)) {
            val up = Point(0.2, -0.7, 0.3)
            val original = solveAndCheck(bounds, up, fill)
            val translated = solveAndCheck(shifted, up, fill, 5e-8)
            assertEquals(original.faces.size, translated.faces.size)
            original.faces.zip(translated.faces).forEach { (a, b) ->
                assertEquals(a.normal, b.normal)
                assertEquals(a.freeSurface, b.freeSurface)
                a.vertices.zip(b.vertices).forEach { (p, q) -> assertPoint(p, q - offset, 1e-8) }
            }
        }
        val thin = Bounds(Point(-2.0, -0.02, -30.0), Point(3.0, 0.03, 50.0))
        for (fill in listOf(0.01, 0.5, 0.99)) solveAndCheck(thin, Point(0.4, 0.3, 0.7), fill)
    }

    @Test fun `lighter than air fills the opposite portion with the same volume`() {
        val up = Point(-0.3, 0.8, 0.4)
        for (fill in listOf(0.01, 0.3, 0.5, 0.99)) {
            val gas = solveAndCheck(bounds, up, fill, lighterThanAir = true)
            val oppositeFluid = TankFluidGeometry.solve(bounds, up * -1.0, fill)
            assertEquals(oppositeFluid, gas)
            val complement = TankFluidGeometry.solve(bounds, up, 1.0 - fill)
            assertEquals(boxVolume(bounds), signedVolume(gas) + signedVolume(complement), 1e-9)
            assertEquals(-complement.surfaceOffset, gas.surfaceOffset, 1e-11)
        }
    }

    @Test fun `invalid bounds fill and gravity fail before producing invalid vertices`() {
        assertThrows(IllegalArgumentException::class.java) { Bounds(Point(0.0, 0.0, 0.0), Point(0.0, 1.0, 1.0)) }
        assertThrows(IllegalArgumentException::class.java) { Bounds(Point(Double.NaN, 0.0, 0.0), Point(1.0, 1.0, 1.0)) }
        assertThrows(IllegalArgumentException::class.java) { TankFluidGeometry.solve(bounds, Point(0.0, 0.0, 0.0), 0.5) }
        assertThrows(IllegalArgumentException::class.java) { TankFluidGeometry.solve(bounds, Point(0.0, 1.0, 0.0), Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { TankFluidGeometry.solve(bounds, Point(Double.POSITIVE_INFINITY, 1.0, 0.0), 0.5) }
    }

    private fun solveAndCheck(box: Bounds, up: Point, fill: Double, tolerance: Double = 1e-10,
                              lighterThanAir: Boolean = false): Mesh {
        val mesh = TankFluidGeometry.solve(box, up, fill, lighterThanAir)
        assertEquals(boxVolume(box) * fill, signedVolume(mesh), boxVolume(box) * tolerance,
            "volume for fill=$fill, up=$up, gas=$lighterThanAir")
        assertEquals(1, mesh.faces.count { it.freeSurface })
        val center = (box.min + box.max) * 0.5
        for (face in mesh.faces) {
            assertTrue(face.vertices.size >= 3)
            val first = face.vertices.first()
            val areaNormal = face.vertices.indices.drop(1).dropLast(1).fold(Point(0.0, 0.0, 0.0)) { sum, i ->
                sum + (face.vertices[i] - first).cross(face.vertices[i + 1] - first)
            }
            assertTrue(areaNormal.dot(face.normal) > 0.0, "outward polygon winding: $face")
            assertEquals(1.0, sqrt(face.normal.dot(face.normal)), 1e-12)
            for (point in face.vertices) {
                assertTrue(point.x.isFinite() && point.y.isFinite() && point.z.isFinite())
                assertTrue(point.x >= box.min.x - 1e-9 && point.x <= box.max.x + 1e-9)
                assertTrue(point.y >= box.min.y - 1e-9 && point.y <= box.max.y + 1e-9)
                assertTrue(point.z >= box.min.z - 1e-9 && point.z <= box.max.z + 1e-9)
                val signedDistance = mesh.surfaceNormal.dot(point - center) -
                    (mesh.surfaceOffset - mesh.surfaceNormal.dot(center))
                assertTrue(signedDistance <= 1e-8, "vertex outside retained half-space: $signedDistance")
                if (face.freeSurface) assertEquals(0.0, signedDistance, 1e-8)
            }
        }
        // Each undirected edge is used twice with opposite direction: the cap closes every cut.
        val directedEdges = mesh.faces.flatMap { face -> face.vertices.indices.map { i ->
            face.vertices[i] to face.vertices[(i + 1) % face.vertices.size]
        } }
        for ((a, b) in directedEdges) {
            assertEquals(1, directedEdges.count { (c, d) -> near(a, d, 1e-8) && near(b, c, 1e-8) },
                "non-manifold boundary edge: $a -> $b")
        }
        return mesh
    }

    /** Independent volume oracle: signed tetrahedra from the mean of boundary vertices. */
    private fun signedVolume(mesh: Mesh): Double {
        val vertices = mesh.faces.flatMap { it.vertices }
        if (vertices.isEmpty()) return 0.0
        val origin = vertices.reduce(Point::plus) * (1.0 / vertices.size)
        var total = 0.0
        for (face in mesh.faces) {
            val a = face.vertices.first() - origin
            for (i in 1 until face.vertices.lastIndex) {
                val b = face.vertices[i] - origin
                val c = face.vertices[i + 1] - origin
                total += a.dot(b.cross(c)) / 6.0
            }
        }
        return total
    }

    private fun rotate(point: Point, angles: Point): Point {
        val rx = Point(point.x, cos(angles.x) * point.y - sin(angles.x) * point.z,
            sin(angles.x) * point.y + cos(angles.x) * point.z)
        val ry = Point(cos(angles.y) * rx.x + sin(angles.y) * rx.z, rx.y,
            -sin(angles.y) * rx.x + cos(angles.y) * rx.z)
        return Point(cos(angles.z) * ry.x - sin(angles.z) * ry.y,
            sin(angles.z) * ry.x + cos(angles.z) * ry.y, ry.z)
    }

    private fun boxVolume(box: Bounds): Double = (box.max.x - box.min.x) * (box.max.y - box.min.y) * (box.max.z - box.min.z)

    private fun near(a: Point, b: Point, tolerance: Double): Boolean =
        abs(a.x - b.x) <= tolerance && abs(a.y - b.y) <= tolerance && abs(a.z - b.z) <= tolerance

    private fun assertPoint(expected: Point, actual: Point, tolerance: Double) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
        assertEquals(expected.z, actual.z, tolerance)
    }
}
