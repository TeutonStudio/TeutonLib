package de.TeutonStudio.TeutonLib.integration.peripheral

import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LinkLocationsTest {
    private fun entry(id: UUID = UUID.randomUUID(), target: UUID = UUID.randomUUID()): LinkLocations.Entry {
        val location = LinkLocations.Location("minecraft:overworld", BlockPos(1000000, 70, 1000000), Vec3(4.5, 70.5, 12.5), "minecraft:chest")
        return LinkLocations.Entry(id, target, "Cargo", location, location.copy(position = location.position.below()), location.copy(block = "computercraft:computer_normal"))
    }
    @Test fun catalogRoundTripKeepsWorldCoordinatesAndUnloadedEndpoints() {
        val catalog = LinkLocations()
        val first = entry()
        val second = entry(target = first.target)
        catalog.put(first); catalog.put(second)
        val restored = LinkLocations.read(catalog.write())
        assertEquals(first, restored.get(first.id))
        assertEquals(2, restored.forTarget(first.target).size)
        assertNotEquals(restored.get(first.id)!!.link.position.center, restored.get(first.id)!!.link.world)
        restored.remove(first.id)
        assertEquals(listOf(second), restored.forTarget(first.target))
    }
    @Test fun observingAMovedIdentityReplacesItsPreviousPositionAndTarget() {
        val catalog = LinkLocations()
        val original = entry()
        catalog.put(original)
        val moved = original.copy(target = UUID.randomUUID(), link = original.link.copy(world = Vec3(25.0, 7.0, 8.0)))
        catalog.put(moved)
        assertTrue(catalog.forTarget(original.target).isEmpty())
        assertEquals(listOf(moved), catalog.all())
        assertNull(LinkLocations.Entry.decode(CompoundTag()))
    }
    @Test fun everyMountFaceCanBeHitWithoutClaimingTheRestOfTheComputer() {
        Direction.entries.forEach { side ->
            val start = LinkAntennaGeometry.orient(Vec3(.5, 2.0, .5), side)
            val end = LinkAntennaGeometry.orient(Vec3(.5, .5, .5), side)
            assertNotNull(LinkAntennaGeometry.hit(BlockPos.ZERO, side, start, end) { it }, side.name)
            val missStart = LinkAntennaGeometry.orient(Vec3(.85, 2.0, .85), side)
            val missEnd = LinkAntennaGeometry.orient(Vec3(.85, .5, .85), side)
            assertNull(LinkAntennaGeometry.hit(BlockPos.ZERO, side, missStart, missEnd) { it }, side.name)
        }
    }
    @Test fun antennasOnTheFarFaceDoNotInterceptClicksOnTheComputer() {
        Direction.entries.forEach { side ->
            val start = LinkAntennaGeometry.orient(Vec3(.5, -1.0, .5), side)
            val surface = LinkAntennaGeometry.orient(Vec3(.5, 0.0, .5), side)
            val end = LinkAntennaGeometry.orient(Vec3(.5, 2.0, .5), side)
            val hit = LinkAntennaGeometry.hit(BlockPos.ZERO, side, start, end) { it }!!
            val obstruction = net.minecraft.world.phys.BlockHitResult(surface, side.opposite, BlockPos.ZERO, false)
            assertTrue(LinkAntennaGeometry.occluded(obstruction, BlockPos.ZERO, start, hit), side.name)
        }
        // The plate slightly overlaps its mounting face, so grazing the plate remains clickable.
        val start = Vec3(.5, 2.0, .5)
        val surface = net.minecraft.world.phys.BlockHitResult(Vec3(.5, 1.0, .5), Direction.UP, BlockPos.ZERO, false)
        assertFalse(LinkAntennaGeometry.occluded(surface, BlockPos.ZERO, start, Vec3(.5, .98, .5)))
    }
    @Test fun antennaFacesUseExplicitUvsInsideTheirAtlasSprite() {
        val model = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/resources/assets/teutonlib/models/block/link_antenna.json"))).asJsonObject
        // Cuboids extend beyond the host block: implicit UVs would sample neighbouring atlas sprites.
        model.getAsJsonArray("elements").forEach { element ->
            element.asJsonObject.getAsJsonObject("faces").entrySet().forEach { (_, face) ->
                val uv = face.asJsonObject.getAsJsonArray("uv")
                assertNotNull(uv)
                assertEquals(4, uv.size())
                assertTrue(uv.all { it.asDouble in 0.0..16.0 })
            }
        }
    }
    @Test fun hitTestsFollowTranslatedRotatedAndScaledSublevels() {
        fun project(v: Vec3) = Vec3(20.0 - v.z * 2, 9.0 + v.y * 2, 30.0 + v.x * 2)
        val localStart = Vec3(.5, 2.0, .5)
        val localEnd = Vec3(.5, .5, .5)
        val hit = LinkAntennaGeometry.hit(BlockPos.ZERO, Direction.UP, project(localStart), project(localEnd), ::project)
        assertNotNull(hit)
        assertTrue(hit!!.distanceTo(project(Vec3(.5, 1.46, .5))) < 1e-8)
        assertNull(LinkAntennaGeometry.hit(BlockPos.ZERO, Direction.UP, project(Vec3(.5, 3.0, .5)), project(Vec3(.5, 2.0, .5)), ::project))
    }
}
