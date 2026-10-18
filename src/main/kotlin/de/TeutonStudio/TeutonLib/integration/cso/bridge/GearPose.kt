package de.TeutonStudio.TeutonLib.integration.cso.bridge

import com.simibubi.create.content.kinetics.base.IRotate
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel
import dev.ryanhcode.sable.Sable
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3d

data class GearPose(
    val gear: KineticBlockEntity, val center: Vec3, val axis: Vector3d,
    val localX: Vector3d, val localY: Vector3d, val localZ: Vector3d,
    val small: Boolean, val large: Boolean, val subLevel: Any?
) {
    fun basis(axis: Direction.Axis) = when (axis) { Direction.Axis.X -> localX; Direction.Axis.Y -> localY; Direction.Axis.Z -> localZ }
    companion object {
        fun of(gear: KineticBlockEntity): GearPose? = create(gear, false)
        fun ofClient(gear: KineticBlockEntity): GearPose? = create(gear, true)
        private fun create(gear: KineticBlockEntity, client: Boolean): GearPose? {
            val state = gear.blockState
            val rotate = state.block as? IRotate ?: return null
            val local = gear.blockPos.center
            val containing = if (client) Sable.HELPER.getContainingClient(gear) else Sable.HELPER.getContaining(gear)
            var center = local
            var orientation = Quaterniond()
            if (containing != null) {
                if (client) {
                    val pose = Sable.HELPER.getContainingClient(gear)!!.renderPose(1.0f)
                    center = pose.transformPosition(local); orientation = Quaterniond(pose.orientation())
                } else {
                    val pose = Sable.HELPER.getContaining(gear.level, local)!!.logicalPose()
                    center = pose.transformPosition(local); orientation = Quaterniond(pose.orientation())
                }
            }
            val x = orientation.transform(Vector3d(1.0, 0.0, 0.0)).normalize()
            val y = orientation.transform(Vector3d(0.0, 1.0, 0.0)).normalize()
            val z = orientation.transform(Vector3d(0.0, 0.0, 1.0)).normalize()
            val axis = when (rotate.getRotationAxis(state)) { Direction.Axis.X -> Vector3d(x); Direction.Axis.Y -> Vector3d(y); Direction.Axis.Z -> Vector3d(z) }
            return GearPose(gear, center, axis, x, y, z, ICogWheel.isSmallCog(state), ICogWheel.isLargeCog(state), containing)
        }
    }
}
