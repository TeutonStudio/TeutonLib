package de.TeutonStudio.TeutonLib.integration.cso.bridge

import com.simibubi.create.content.kinetics.base.IRotate
import net.minecraft.core.Direction
import org.joml.Vector3d
import kotlin.math.abs
import kotlin.math.sqrt

object GearGeometry {
    private const val POSITION_TOLERANCE = 0.14
    private const val AXIS_PARALLEL_DOT = 0.9925
    private const val AXIS_PERP_DOT = 0.12
    fun modifier(from: GearPose?, to: GearPose?): Float {
        if (from == null || to == null || from.gear === to.gear || from.subLevel === to.subLevel) return 0f
        val delta = Vector3d(to.center.x - from.center.x, to.center.y - from.center.y, to.center.z - from.center.z)
        val distanceSq = delta.lengthSquared()
        if (distanceSq !in 0.60..2.55) return 0f
        val dots = abs(from.axis.dot(to.axis))
        if (from.small && to.small && !from.large && !to.large) return if (abs(sqrt(distanceSq) - 1.0) <= POSITION_TOLERANCE && dots >= AXIS_PARALLEL_DOT && abs(delta.dot(from.axis)) <= POSITION_TOLERANCE) -1f else 0f
        if (from.large && to.large) {
            if (abs(from.axis.dot(to.axis)) > AXIS_PERP_DOT) return 0f
            val a = delta.dot(from.axis); val b = delta.dot(to.axis)
            val residual = Vector3d(delta).fma(-a, from.axis).fma(-b, to.axis)
            return if (abs(abs(a) - 1.0) <= POSITION_TOLERANCE && abs(abs(b) - 1.0) <= POSITION_TOLERANCE && residual.length() <= POSITION_TOLERANCE) if ((a > 0) xor (b > 0)) -1f else 1f else 0f
        }
        if (from.large && strictSmall(to)) return if (largeToSmall(from, to)) -2f else 0f
        if (strictSmall(from) && to.large) return if (largeToSmall(to, from)) -.5f else 0f
        return 0f
    }
    private fun strictSmall(pose: GearPose) = pose.small && !pose.large
    private fun largeToSmall(large: GearPose, small: GearPose): Boolean {
        if (abs(large.axis.dot(small.axis)) < AXIS_PARALLEL_DOT) return false
        val delta = Vector3d(small.center.x - large.center.x, small.center.y - large.center.y, small.center.z - large.center.z)
        if (abs(delta.dot(large.axis)) > POSITION_TOLERANCE) return false
        val rotation = (large.gear.blockState.block as? IRotate)?.getRotationAxis(large.gear.blockState) ?: return false
        val other = Direction.Axis.entries.filter { it != rotation }
        return abs(abs(delta.dot(large.basis(other[0]))) - 1.0) <= POSITION_TOLERANCE && abs(abs(delta.dot(large.basis(other[1]))) - 1.0) <= POSITION_TOLERANCE
    }
}
