package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dan200.computercraft.api.lua.LuaException
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkDevice
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkExtensionMethod
import de.TeutonStudio.TeutonLib.mixin.peripheral.simulated.SimPeripheralAccessor
import dev.ryanhcode.sable.Sable
import dev.simulated_team.simulated.compat.computercraft.peripherals.GimbalSensorPeripheral
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity
import org.joml.Vector3d

internal object GimbalPeripheralExtensions {
    fun methods(device: LinkDevice): Map<String, LinkExtensionMethod> {
        val peripheral = device.peripheral()
        if (peripheral !is GimbalSensorPeripheral) return emptyMap()
        val block = (peripheral as SimPeripheralAccessor).`teutonlib$blockEntity`()
        if (block !is GimbalSensorBlockEntity) return emptyMap()
        return mapOf(
            "getYaw" to LinkExtensionMethod { context, arguments ->
                if (arguments.count() != 0) throw LuaException("getYaw expects no arguments")
                context.executeMainThreadTask {
                    device.info()
                    val sublevel = Sable.HELPER.getContaining(block)
                        ?: throw LuaException("Gimbal sensor is not inside a Sable sublevel")
                    val forward = sublevel.logicalPose().orientation().transform(Vector3d(0.0, 0.0, 1.0))
                    val yaw = try {
                        HeliMath.yawDegrees(forward)
                    } catch (failure: IllegalArgumentException) {
                        throw LuaException(failure.message ?: "Could not determine yaw")
                    }
                    arrayOf(yaw)
                }
            }
        )
    }
}
