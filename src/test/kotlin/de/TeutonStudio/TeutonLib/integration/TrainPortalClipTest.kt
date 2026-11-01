package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.integration.train.TrainPortalClip
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class TrainPortalClipTest {
    private val x = Vec3(1.0, 0.0, 0.0)
    private val y = Vec3(0.0, 1.0, 0.0)
    private val z = Vec3(0.0, 0.0, 1.0)

    private fun touches(center: Vec3, halfX: Vec3 = x, halfY: Vec3 = y, halfZ: Vec3 = z.scale(0.5)) =
        TrainPortalClip.touches(center, halfX, halfY, halfZ, Vec3.ZERO, z, x, y, 1.0, 1.0)

    @Test fun `overlap depends on the build volume and the finite opening`() {
        assertFalse(touches(Vec3(0.0, 0.0, -2.0)), "fully before the plane")
        assertTrue(touches(Vec3(0.0, 0.0, -0.25)), "front overhang through the plane")
        assertTrue(touches(Vec3.ZERO), "stationary across the plane")
        assertTrue(touches(Vec3(0.0, 0.0, 0.25)), "rear overhang still through the plane")
        assertFalse(touches(Vec3(0.0, 0.0, 2.0)), "fully beyond the plane")
        assertFalse(touches(Vec3(2.1, 0.0, 0.0)), "beside the finite opening")
        assertFalse(touches(Vec3(0.0, 2.1, 0.0)), "above the finite opening")
        assertFalse(touches(Vec3(100.0, 100.0, 100.0)), "fully away")
    }

    @Test fun `small skin keeps a seam stable without admitting distant builds`() {
        assertTrue(touches(Vec3(0.0, 0.0, 0.519)))
        assertFalse(touches(Vec3(0.0, 0.0, 0.521)))
        assertTrue(touches(Vec3(2.019, 0.0, 0.0)))
        assertFalse(touches(Vec3(2.021, 0.0, 0.0)))
    }

    @Test fun `ninety degree body rotation uses the actual half extents`() {
        val longX = x.scale(2.0)
        val narrowY = y.scale(0.2)
        val narrowZ = z.scale(0.1)
        assertFalse(touches(Vec3(0.0, 0.0, 1.9), longX, narrowY, narrowZ))
        assertTrue(touches(Vec3(0.0, 0.0, 1.9), z.scale(-2.0), narrowY, x.scale(0.1)))
    }

    @Test fun `diagonal body does not mistake an overlapping world AABB for a crossing`() {
        val diagonal = 1.0 / sqrt(2.0)
        val halfX = Vec3(diagonal * 2.0, diagonal * 2.0, 0.0)
        val halfY = Vec3(-diagonal * 0.1, diagonal * 0.1, 0.0)
        assertTrue(touches(Vec3(1.5, 1.5, 0.0), halfX, halfY, z.scale(0.1)))
        assertFalse(touches(Vec3(2.0, -2.0, 0.0), halfX, halfY, z.scale(0.1)))
    }

    @Test fun `tilted edge misses the aperture even when face projections overlap`() {
        // A thin beam tilted around X and Y reaches the plane beyond the portal's edge.
        // This needs an edge-pair separating axis, not just the box and portal faces.
        val diagonal = 1.0 / sqrt(2.0)
        val halfX = Vec3(2.0 * diagonal, 0.0, -2.0 * diagonal)
        val halfY = Vec3(0.1, 0.2 * diagonal, 0.1)
        val halfZ = Vec3(0.075, -0.15 * diagonal, 0.075)
        assertTrue(touches(Vec3(0.0, 0.0, 0.5), halfX, halfY, halfZ))
        assertFalse(touches(Vec3(0.0, 0.0, 1.5), halfX, halfY, halfZ))
    }

    @Test fun `thin and degenerate build dimensions remain testable`() {
        assertTrue(touches(Vec3.ZERO, x.scale(0.4), y.scale(0.4), Vec3.ZERO))
        assertFalse(touches(Vec3(0.0, 0.0, 0.03), x.scale(0.4), y.scale(0.4), Vec3.ZERO))
        assertTrue(touches(Vec3(0.0, 0.0, 0.9), Vec3.ZERO, Vec3.ZERO, z))
        assertFalse(touches(Vec3(1.1, 0.0, 0.9), Vec3.ZERO, Vec3.ZERO, z))
        assertTrue(touches(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO))
        assertFalse(touches(Vec3(0.0, 0.0, 0.03), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO))
    }

    @Test fun `portal and build may be rotated and translated together`() {
        val origin = Vec3(29.0, -7.0, 44.0)
        val diagonal = 1.0 / sqrt(2.0)
        val w = Vec3(diagonal, 0.0, -diagonal)
        val h = y
        val n = Vec3(diagonal, 0.0, diagonal)
        fun mapped(local: Vec3) = origin.add(w.scale(local.x)).add(h.scale(local.y)).add(n.scale(local.z))
        assertTrue(TrainPortalClip.touches(mapped(Vec3(0.0, 0.0, -0.25)), w, h, n.scale(0.5),
            origin, n, w, h, 1.0, 1.0))
        assertFalse(TrainPortalClip.touches(mapped(Vec3(2.1, 0.0, 0.0)), w, h, n.scale(0.5),
            origin, n, w, h, 1.0, 1.0))
    }
}
