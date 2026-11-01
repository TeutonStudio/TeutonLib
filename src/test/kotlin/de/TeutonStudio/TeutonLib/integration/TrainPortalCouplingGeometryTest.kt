package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalCouplingGeometry
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalCouplingGeometry.Plane
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import qouteall.imm_ptl.core.compat.sable_straddle.IPPortalIsometry

class TrainPortalCouplingGeometryTest {
    private data class NativeCopy(val world: String, val ready: Boolean)
    private fun assertPoint(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-7)
        assertEquals(expected.y, actual.y, 1e-7)
        assertEquals(expected.z, actual.z, 1e-7)
    }

    @Test fun `mixed coupling retains the ready far endpoint when its local copy is absent or pending`() {
        val far = NativeCopy("nether", true)
        val pending = NativeCopy("overworld", false)
        fun choose(copies: List<NativeCopy>) = TrainPortalCouplingGeometry.preferredEndpoint(copies,
            { it.ready }, { it.world == "overworld" })
        assertSame(far, choose(listOf(far)))
        assertSame(far, choose(listOf(pending, far)))
        val local = NativeCopy("overworld", true)
        assertSame(local, choose(listOf(far, pending, local)))
        assertNull(choose(listOf(pending)))
    }

    @Test fun `remote coupling yields two positive short segments meeting their portal planes`() {
        val origin = Vec3(830.0, 70.0, -1250.0)
        val destination = Vec3(910.0, 70.0, -1250.0)
        val normal = Vec3(1.0, 0.0, 0.0)
        val portal = IPPortalIsometry(origin, destination, Quaterniond().rotationY(Math.PI / 2))
        val first = origin.add(4.0, 0.0, 0.0)
        val second = portal.mapPoint(origin.add(-4.0, 0.0, 0.0))
        assertFalse(TrainPortalCouplingGeometry.plausible(first, second, 12.0), "raw world-space rod must be rejected")

        val secondInSource = portal.unmapPoint(second)
        assertTrue(TrainPortalCouplingGeometry.plausible(first, secondInSource, 12.0))
        val source = TrainPortalCouplingGeometry.clip(first, secondInSource, listOf(Plane(origin, normal)))!!
        val target = TrainPortalCouplingGeometry.clip(portal.mapPoint(first), second,
            listOf(Plane(destination, portal.mapVec(normal).scale(-1.0))))!!

        assertEquals(4.0, source.start.distanceTo(source.end), 1e-7)
        assertEquals(4.0, target.start.distanceTo(target.end), 1e-7)
        assertPoint(origin, source.end)
        assertPoint(destination, target.start)
        assertTrue(source.firstHead)
        assertFalse(source.secondHead, "no phantom coupler head at the source cut")
        assertFalse(target.firstHead, "no phantom coupler head at the target cut")
        assertTrue(target.secondHead)
    }

    @Test fun `same side coupling stays fully visible and an invisible projection is omitted`() {
        val plane = Plane(Vec3.ZERO, Vec3(1.0, 0.0, 0.0))
        val whole = TrainPortalCouplingGeometry.clip(Vec3(2.0, 0.0, 0.0), Vec3(7.0, 0.0, 0.0), listOf(plane))!!
        assertTrue(whole.firstHead && whole.secondHead)
        assertEquals(5.0, whole.start.distanceTo(whole.end), 1e-7)
        assertNull(TrainPortalCouplingGeometry.clip(Vec3(-2.0, 0.0, 0.0), Vec3(-7.0, 0.0, 0.0), listOf(plane)))
    }

    @Test fun `reverse travel keeps the opposite half without losing either portal segment`() {
        val portal = IPPortalIsometry(Vec3.ZERO, Vec3(80.0, 0.0, 0.0), Quaterniond().rotationY(-Math.PI / 2))
        val keep = Vec3(-1.0, 0.0, 0.0)
        val first = Vec3(-3.0, 0.0, 0.0)
        val second = portal.mapPoint(Vec3(5.0, 0.0, 0.0))
        val source = TrainPortalCouplingGeometry.clip(first, portal.unmapPoint(second), listOf(Plane(Vec3.ZERO, keep)))!!
        val target = TrainPortalCouplingGeometry.clip(portal.mapPoint(first), second,
            listOf(Plane(portal.dest(), portal.mapVec(keep).scale(-1.0))))!!
        assertEquals(3.0, source.start.distanceTo(source.end), 1e-7)
        assertEquals(5.0, target.start.distanceTo(target.end), 1e-7)
        assertPoint(Vec3.ZERO, source.end)
        assertPoint(portal.dest(), target.start)
        assertTrue(source.firstHead && target.secondHead)
        assertFalse(source.secondHead || target.firstHead)
    }

    @Test fun `finite opening accepts the actual crossing and rejects an unrelated nearby portal`() {
        fun crosses(y: Double) = TrainPortalCouplingGeometry.crossesOpening(
            Vec3(4.0, y, 0.0), Vec3(-4.0, y, 0.0), Vec3.ZERO,
            Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.0, 1.0, 0.0), 3.0, 4.0)
        assertTrue(crosses(1.0))
        assertFalse(crosses(8.0))
        assertFalse(TrainPortalCouplingGeometry.crossesOpening(Vec3(4.0, 0.0, 0.0), Vec3(2.0, 0.0, 0.0),
            Vec3.ZERO, Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.0, 1.0, 0.0), 3.0, 4.0))
    }

    @Test fun `nested portal passes retain only the shared visible interval`() {
        val segment = TrainPortalCouplingGeometry.clip(Vec3(-8.0, 0.0, 0.0), Vec3(8.0, 0.0, 0.0), listOf(
            Plane(Vec3(-2.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)),
            Plane(Vec3(3.0, 0.0, 0.0), Vec3(-1.0, 0.0, 0.0))))!!
        assertPoint(Vec3(-2.0, 0.0, 0.0), segment.start)
        assertPoint(Vec3(3.0, 0.0, 0.0), segment.end)
        assertFalse(segment.firstHead || segment.secondHead)
    }
}
