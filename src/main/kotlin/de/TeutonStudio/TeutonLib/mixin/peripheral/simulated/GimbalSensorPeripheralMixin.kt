package de.TeutonStudio.TeutonLib.mixin.peripheral.simulated

import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import de.TeutonStudio.TeutonLib.integration.peripheral.heli.GimbalHeading
import dev.simulated_team.simulated.compat.computercraft.peripherals.GimbalSensorPeripheral
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique

@Mixin(value = [GimbalSensorPeripheral::class], remap = false)
abstract class GimbalSensorPeripheralMixin {
    @LuaFunction(mainThread = true)
    open fun getYaw(): Double = `teutonlib$yaw`(`teutonlib$sensor`())

    @LuaFunction(mainThread = true)
    open fun getPitch(): Double = Math.toDegrees(`teutonlib$sensor`().xAngle)

    @LuaFunction(mainThread = true)
    open fun getRoll(): Double = Math.toDegrees(`teutonlib$sensor`().zAngle)

    @LuaFunction(mainThread = true)
    open fun getRotation(): List<Double> {
        val sensor = `teutonlib$sensor`()
        return listOf(Math.toDegrees(sensor.xAngle), `teutonlib$yaw`(sensor), Math.toDegrees(sensor.zAngle))
    }

    /** @deprecated Misspelled compatibility alias; use getRotation. */
    @Deprecated("Use getRotation")
    @LuaFunction(mainThread = true)
    open fun getRotaion(): List<Double> = getRotation()

    @Unique
    private fun `teutonlib$sensor`(): GimbalSensorBlockEntity =
        (this as SimPeripheralAccessor).`teutonlib$blockEntity`() as GimbalSensorBlockEntity

    @Unique
    private fun `teutonlib$yaw`(sensor: GimbalSensorBlockEntity): Double = try {
        GimbalHeading.degrees(sensor)
    } catch (failure: IllegalArgumentException) {
        throw LuaException(failure.message ?: "Could not determine gimbal sensor yaw")
    }
}
