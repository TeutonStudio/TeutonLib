package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import org.joml.Vector3d
import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.acos

class HeliMathTest {
    @Test
    fun normalizesAndRejectsZeroVectors() {
        val result = HeliMath.normalized(Vector3d(3.0, 0.0, 4.0))
        assertEquals(0.6, result.x, 1.0e-12)
        assertEquals(0.8, result.z, 1.0e-12)
        assertThrows(IllegalArgumentException::class.java) { HeliMath.normalized(Vector3d()) }
    }

    @Test
    fun combinesRollAndPitchAroundSublevelAxes() {
        val result = HeliMath.adjustTilt(Vector3d(0.0, 1.0, 0.0), 10.0, -5.0)
        assertTrue(result.x < 0.0, "positive roll tilts local up toward -X")
        assertTrue(result.z < 0.0, "negative pitch tilts local up toward -Z")
        assertEquals(1.0, result.length(), 1.0e-12)
    }

    @Test
    fun clampsToTwelveDegreeCone() {
        val axis = Vector3d(0.0, 1.0, 0.0)
        val result = HeliMath.clampToCone(Vector3d(1.0, 0.0, 0.0), axis)
        assertEquals(12.0, Math.toDegrees(acos(result.dot(axis))), 1.0e-9)
    }

    @Test
    fun yawUsesMinecraftAxesAndWrapsAtThreeSixty() {
        assertEquals(0.0, HeliMath.yawDegrees(Vector3d(0.0, 0.0, 1.0)), 1.0e-12)
        assertEquals(90.0, HeliMath.yawDegrees(Vector3d(-1.0, 0.0, 0.0)), 1.0e-12)
        assertEquals(180.0, HeliMath.yawDegrees(Vector3d(0.0, 0.0, -1.0)), 1.0e-12)
        assertEquals(270.0, HeliMath.yawDegrees(Vector3d(1.0, 0.0, 0.0)), 1.0e-12)
        assertTrue(HeliMath.yawDegrees(Vector3d(1.0e-6, 0.0, 1.0)) > 359.0)
        assertTrue(HeliMath.yawDegrees(Vector3d(-1.0e-6, 0.0, 1.0)) < 1.0)
        assertThrows(IllegalArgumentException::class.java) { HeliMath.yawDegrees(Vector3d(0.0, 1.0, 0.0)) }
        assertThrows(IllegalArgumentException::class.java) {
            HeliMath.yawDegrees(Vector3d(Double.NaN, 0.0, 1.0))
        }
    }

    @Test
    fun staticGimbalAxesUseSimulatedVisualPositiveDirection() {
        assertEquals(270.0, HeliMath.yawDegrees(GimbalHeading.staticForward(Direction.Axis.X)), 1.0e-12)
        assertEquals(0.0, HeliMath.yawDegrees(GimbalHeading.staticForward(Direction.Axis.Z)), 1.0e-12)
        assertThrows(IllegalArgumentException::class.java) { GimbalHeading.staticForward(Direction.Axis.Y) }
    }

    @Test
    fun targetExpiresExactlyTwentyTicksAfterLastUpdate() {
        assertTrue(HeliMath.targetIsActive(119L, 100L))
        assertFalse(HeliMath.targetIsActive(120L, 100L))
        assertFalse(HeliMath.targetIsActive(99L, 100L))
    }
}
