package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dan200.computercraft.api.lua.LuaException
import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.gyroscopic_propeller_bearing.GyroscopicPropellerBearingBlockEntity
import dev.ryanhcode.sable.Sable
import net.minecraft.core.Direction
import net.minecraft.world.level.block.DirectionalBlock
import org.joml.Vector3d
import org.joml.Vector3dc

internal object BearingControl {
    @Throws(LuaException::class)
    fun adjustTilt(block: GyroscopicPropellerBearingBlockEntity, table: Map<*, *>) {
        val bearing = block as GyroscopicBearingAccess
        val values = HeliArguments.vectorTable(table, setOf("deltaRoll", "deltaPitch"))
        val base = bearing.`teutonlib$activeLuaTiltTarget`() ?: bearing.`teutonlib$currentTiltDirection`()
        val adjusted = HeliMath.adjustTilt(base, values.getValue("deltaRoll"), values.getValue("deltaPitch"))
        bearing.`teutonlib$setLuaTiltTarget`(clampForBearing(block, adjusted))
    }

    @Throws(LuaException::class)
    fun setTargetDirection(block: GyroscopicPropellerBearingBlockEntity, table: Map<*, *>) {
        val values = HeliArguments.vectorTable(table, setOf("x", "y", "z"))
        val world = try {
            HeliMath.normalized(Vector3d(values.getValue("x"), values.getValue("y"), values.getValue("z")))
        } catch (failure: IllegalArgumentException) {
            throw LuaException(failure.message ?: "Invalid direction")
        }
        val local = Sable.HELPER.getContaining(block)?.logicalPose()?.orientation()
            ?.transformInverse(Vector3d(world)) ?: world
        (block as GyroscopicBearingAccess).`teutonlib$setLuaTiltTarget`(clampForBearing(block, local))
    }

    private fun clampForBearing(block: GyroscopicPropellerBearingBlockEntity, target: Vector3dc): Vector3d {
        val facing = block.blockState.getValue<Direction>(DirectionalBlock.FACING)
        return HeliMath.clampToCone(
            target,
            Vector3d(facing.stepX.toDouble(), facing.stepY.toDouble(), facing.stepZ.toDouble())
        )
    }
}
