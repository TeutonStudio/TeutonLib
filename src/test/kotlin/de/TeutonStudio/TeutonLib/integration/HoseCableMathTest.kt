package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.integration.hose.HoseCableMath
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HoseCableMathTest {
    @Test fun `contact nodes interpolate by arc distance instead of shifting node indices`() {
        val source = listOf(Vec3.ZERO, Vec3(0.0,-2.0,0.0), Vec3(4.0,-2.0,0.0))
        val target = listOf(Vec3.ZERO, Vec3(0.0,-1.0,0.0), Vec3(0.0,-2.0,0.0), Vec3(4.0,-2.0,0.0))
        assertEquals(target,HoseCableMath.matchTopology(source,target))
        assertEquals(source,HoseCableMath.matchTopology(target,source))
    }
    @Test fun `interpolation preserves endpoints and handles coincident nodes`() {
        val source = listOf(Vec3.ZERO,Vec3.ZERO,Vec3(0.0,2.0,0.0))
        val target = listOf(Vec3.ZERO,Vec3(0.0,1.0,0.0),Vec3(0.0,2.0,0.0))
        assertEquals(target,HoseCableMath.matchTopology(source,target))
        assertEquals(List(3) { Vec3.ZERO },HoseCableMath.matchTopology(listOf(Vec3.ZERO,Vec3.ZERO),target))
        assertEquals(target,HoseCableMath.matchTopology(target,List(3) { Vec3.ZERO }))
    }
    @Test fun `a route over an obstacle spends length before the final leg`() {
        val path = listOf(Vec3.ZERO, Vec3(0.0,3.0,0.0), Vec3(4.0,3.0,0.0), Vec3(4.0,0.0,0.0))
        assertEquals(10.0,HoseCableMath.length(path),1e-12)
        val limited = HoseCableMath.limitEnd(path,Vec3(4.0,-2.0,0.0),10.0)
        assertEquals(Vec3(4.0,0.0,0.0),limited)
        assertEquals(Vec3(0.0,1.0,0.0),HoseCableMath.startTangent(path))
        assertEquals(Vec3(0.0,1.0,0.0),HoseCableMath.endTangent(path))
    }
    @Test fun `zero length and coincident nodes have finite limits and directions`() {
        val points = listOf(Vec3.ZERO,Vec3.ZERO)
        assertEquals(Vec3.ZERO,HoseCableMath.limitEnd(points,Vec3(20.0,1.0,0.0),0.0))
        assertEquals(Vec3.ZERO,HoseCableMath.startTangent(points))
        assertEquals(Vec3.ZERO,HoseCableMath.endTangent(points))
        assertEquals(1,HoseCableMath.segmentCount(0.0))
        assertEquals(257,HoseCableMath.segmentCount(256.35))
    }
    @Test fun `saved and network routes reject nonfinite or oversized data`() {
        assertFalse(HoseCableMath.valid(emptyList()))
        assertFalse(HoseCableMath.valid(listOf(Vec3.ZERO,Vec3(Double.NaN,0.0,0.0))))
        assertFalse(HoseCableMath.valid(List(1028) { Vec3.ZERO }))
        assertTrue(HoseCableMath.valid(listOf(Vec3.ZERO,Vec3(0.0,1.0,0.0))))
    }
}
