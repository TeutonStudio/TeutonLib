package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.integration.train.TrainPortalProjectedSupport
import dev.ryanhcode.sable.companion.math.Pose3d
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrainPortalProjectedSupportTest {
    private fun pose(x: Double, z: Double, turn: Double = 0.0) = Pose3d().also {
        it.position().set(x, 100.0, z)
        it.rotationPoint().set(20481032.0, 128.0, 20483080.0)
        it.orientation().rotateY(Math.toRadians(turn))
    }

    private fun assertPosition(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-7)
        assertEquals(expected.y, actual.y, 1e-7)
        assertEquals(expected.z, actual.z, 1e-7)
    }

    @Test fun `stationary projected floor does not accumulate movement`() {
        val stopped = pose(10.0, 20.0, 90.0)
        var player = Vec3(13.25, 101.0, 26.5)
        repeat(100) {
            player = player.add(TrainPortalProjectedSupport.displacement(stopped, stopped, player))
        }
        assertPosition(Vec3(13.25, 101.0, 26.5), player)
    }

    @Test fun `floor turn and translation preserve the players independent walk offset`() {
        val before = pose(10.0, 20.0)
        val after = pose(15.0, 30.0, 90.0)
        // The rider walked half a block along X and a quarter along Z before this tick.
        val walked = Vec3(13.5, 101.0, 18.25)
        val carried = walked.add(TrainPortalProjectedSupport.displacement(before, after, walked))
        assertPosition(Vec3(13.25, 101.0, 26.5), carried)
        assertPosition(walked, carried.add(TrainPortalProjectedSupport.displacement(after, before, carried)))
    }
}
