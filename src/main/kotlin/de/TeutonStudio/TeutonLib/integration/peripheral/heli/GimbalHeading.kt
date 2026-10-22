package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dev.ryanhcode.sable.Sable
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlock
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity
import net.minecraft.core.Direction
import org.joml.Vector3d

internal object GimbalHeading {
    fun degrees(sensor: GimbalSensorBlockEntity): Double {
        val sublevel = Sable.HELPER.getContaining(sensor)
        val forward = if (sublevel != null) {
            sublevel.logicalPose().orientation().transform(Vector3d(0.0, 0.0, 1.0))
        } else {
            staticForward(sensor.blockState.getValue(GimbalSensorBlock.HORIZONTAL_AXIS))
        }
        return HeliMath.yawDegrees(forward)
    }

    fun staticForward(axis: Direction.Axis): Vector3d = when (axis) {
        Direction.Axis.X -> Vector3d(1.0, 0.0, 0.0)
        Direction.Axis.Z -> Vector3d(0.0, 0.0, 1.0)
        else -> throw IllegalArgumentException("Gimbal sensor has no horizontal axis")
    }
}
