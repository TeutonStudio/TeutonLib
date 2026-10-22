package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpticalSensorPeripheralExtensionsTest {
    @Test
    fun exposesEveryLaserFacingAsNamedLocalUnitVector() {
        Direction.entries.forEach { direction ->
            assertEquals(
                mapOf("x" to direction.stepX, "y" to direction.stepY, "z" to direction.stepZ),
                OpticalSensorPeripheralExtensions.vector(direction)
            )
        }
    }
}
