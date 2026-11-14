package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.integration.hose.HoseCableContacts
import de.TeutonStudio.TeutonLib.integration.hose.HoseCableMath
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class HoseCableContactsTest {
    private fun clear(box: AABB, path: List<Vec3>) {
        val inside = box.deflate(.0001)
        for ((a,b) in path.zipWithNext()) {
            assertFalse(inside.contains(a) || inside.contains(b) || inside.clip(a,b).isPresent, "Route crosses obstacle: $path")
        }
    }

    @Test fun `a planar hose crosses a wall without detouring sideways to voxel corners`() {
        val box = AABB(0.0,0.0,-2.0,1.0,1.0,2.0)
        val a = Vec3(-1.0,.8,0.0); val b = Vec3(2.0,.8,0.0)
        val path = checkNotNull(HoseCableContacts.route(box,a,b,listOf(a,b)))
        assertTrue(path.all { it.z == 0.0 }, "Planar contact jumped sideways: $path")
        assertEquals(1.0+2*sqrt(1.04),HoseCableMath.length(path),1e-9)
        clear(box,path)
    }

    @Test fun `small endpoint noise does not switch between equally short obstacle sides`() {
        val box = AABB(0.0,-1.0,-4.0,1.0,1.0,4.0)
        val previous = listOf(Vec3(-1.0,0.0,0.0),Vec3(0.0,1.0,0.0),Vec3(1.0,1.0,0.0),Vec3(2.0,0.0,0.0))
        for (offset in listOf(-.001,.001,-.002,.002)) {
            val path = checkNotNull(HoseCableContacts.route(box,Vec3(-1.0,offset,0.0),Vec3(2.0,offset,0.0),previous))
            assertTrue(path.drop(1).dropLast(1).all { it.y == 1.0 }, "Contact changed sides: $path")
            clear(box,path)
        }
    }

    @Test fun `contact preference yields to a moved obstacle and to an unobstructed chord`() {
        val box = AABB(0.0,-1.0,-4.0,1.0,3.0,4.0)
        val a = Vec3(-1.0,0.0,0.0); val b = Vec3(2.0,0.0,0.0)
        val previous = listOf(a,Vec3(0.0,1.0,0.0),Vec3(1.0,1.0,0.0),b)
        val path = checkNotNull(HoseCableContacts.route(box,a,b,previous))
        assertTrue(path.drop(1).dropLast(1).all { it.y == -1.0 })
        clear(box,path)
        val aboveA = a.add(0.0,4.0,0.0); val aboveB = b.add(0.0,4.0,0.0)
        assertEquals(listOf(aboveA,aboveB),HoseCableContacts.route(box,aboveA,aboveB,previous))
    }
}
