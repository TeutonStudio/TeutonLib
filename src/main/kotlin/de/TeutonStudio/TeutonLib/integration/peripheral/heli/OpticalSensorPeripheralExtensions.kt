package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import de.TeutonStudio.TeutonLib.integration.peripheral.LinkDevice
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkExtensionMethod
import de.TeutonStudio.TeutonLib.mixin.peripheral.simulated.SimPeripheralAccessor
import dev.simulated_team.simulated.compat.computercraft.peripherals.OpticalSensorPeripheral
import dev.simulated_team.simulated.content.blocks.lasers.optical_sensor.OpticalSensorBlockEntity
import net.minecraft.core.Direction

/** Adds the laser's plot-local direction to optical sensors reached through a Peripheral Link. */
internal object OpticalSensorPeripheralExtensions {
    fun methods(device: LinkDevice): Map<String, LinkExtensionMethod> {
        val peripheral = device.peripheral()
        if (peripheral !is OpticalSensorPeripheral) return emptyMap()
        val sensor = (peripheral as SimPeripheralAccessor).`teutonlib$blockEntity`() as OpticalSensorBlockEntity
        return mapOf(
            "getLocalDirection" to LinkExtensionMethod { context, _ ->
                context.executeMainThreadTask {
                    device.info()
                    arrayOf(vector(sensor.direction))
                }
            }
        )
    }

    fun vector(direction: Direction): Map<String, Int> = linkedMapOf(
        "x" to direction.stepX,
        "y" to direction.stepY,
        "z" to direction.stepZ
    )
}
