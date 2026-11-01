package de.TeutonStudio.TeutonLib.integration.train

import dev.ryanhcode.sable.companion.math.Pose3dc
import dev.ryanhcode.sable.sublevel.SubLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/** Resolve contact against one wagon's blocks, including collision shapes taller than a block. */
object TrainPortalProjectedSupport {
    fun supportsFloor(sub: SubLevel, pose: Pose3dc, position: Vec3,
        keeps: (BlockPos) -> Boolean = { true }): Boolean {
        val feet = pose.transformPositionInverse(position)
        val floor = BlockPos.containing(feet.subtract(0.0, 0.2, 0.0))
        val bounds = sub.plot.boundingBox
        for (block in listOf(floor, floor.below())) {
            if (block.x < bounds.minX() || block.x > bounds.maxX() ||
                block.y < bounds.minY() || block.y > bounds.maxY() ||
                block.z < bounds.minZ() || block.z > bounds.maxZ() || !keeps(block)) continue
            val shape = sub.level.getBlockState(block).getCollisionShape(sub.level, block)
            if (shape.toAabbs().any { box ->
                feet.x >= block.x + box.minX - 1e-5 && feet.x <= block.x + box.maxX + 1e-5 &&
                    feet.z >= block.z + box.minZ - 1e-5 && feet.z <= block.z + box.maxZ + 1e-5 &&
                    kotlin.math.abs(feet.y - block.y - box.maxY) < 0.25
            }) return true
        }
        return false
    }

    /** Preserve walking relative to the floor while applying only its change of pose. */
    fun displacement(previous: Pose3dc, current: Pose3dc, position: Vec3): Vec3 =
        current.transformPosition(previous.transformPositionInverse(position)).subtract(position)
}
