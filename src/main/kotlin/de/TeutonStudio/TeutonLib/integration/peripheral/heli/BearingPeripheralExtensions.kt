package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.LuaException
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkDevice
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkExtensionMethod
import dev.ryanhcode.sable.Sable
import net.minecraft.core.Direction
import net.minecraft.world.level.block.DirectionalBlock
import org.joml.Vector3d

internal object BearingPeripheralExtensions {
    fun methods(device: LinkDevice): Map<String, LinkExtensionMethod> {
        val peripheral = device.peripheral()
        if (peripheral !is GyroscopicBearingPeripheral) return emptyMap()
        val bearing = peripheral.blockEntity as GyroscopicBearingAccess
        return mapOf(
            "adjustTilt" to LinkExtensionMethod { context, arguments ->
                val values = vectorTable(arguments, setOf("deltaRoll", "deltaPitch"))
                context.executeMainThreadTask {
                    device.info()
                    val base = bearing.`teutonlib$activeLuaTiltTarget`() ?: bearing.`teutonlib$currentTiltDirection`()
                    val adjusted = HeliMath.adjustTilt(base, values.getValue("deltaRoll"), values.getValue("deltaPitch"))
                    bearing.`teutonlib$setLuaTiltTarget`(clampForBearing(bearing, adjusted))
                    emptyArray()
                }
            },
            "setTargetDirection" to LinkExtensionMethod { context, arguments ->
                val values = vectorTable(arguments, setOf("x", "y", "z"))
                val world = try {
                    HeliMath.normalized(Vector3d(values.getValue("x"), values.getValue("y"), values.getValue("z")))
                } catch (failure: IllegalArgumentException) {
                    throw LuaException(failure.message ?: "Invalid direction")
                }
                context.executeMainThreadTask {
                    device.info()
                    val block = bearing as Any as net.minecraft.world.level.block.entity.BlockEntity
                    val local = Sable.HELPER.getContaining(block)?.logicalPose()?.orientation()
                        ?.transformInverse(Vector3d(world)) ?: world
                    bearing.`teutonlib$setLuaTiltTarget`(clampForBearing(bearing, local))
                    emptyArray()
                }
            }
        )
    }

    private fun clampForBearing(bearing: GyroscopicBearingAccess, target: Vector3d): Vector3d {
        val block = bearing as Any as net.minecraft.world.level.block.entity.BlockEntity
        val facing = block.blockState.getValue<Direction>(DirectionalBlock.FACING)
        return HeliMath.clampToCone(
            target,
            Vector3d(facing.stepX.toDouble(), facing.stepY.toDouble(), facing.stepZ.toDouble())
        )
    }

    @Throws(LuaException::class)
    private fun vectorTable(arguments: IArguments, keys: Set<String>): Map<String, Double> {
        if (arguments.count() != 1) throw LuaException("Expected one table containing ${keys.joinToString()}")
        return HeliArguments.vectorTable(arguments.getTable(0), keys)
    }
}
