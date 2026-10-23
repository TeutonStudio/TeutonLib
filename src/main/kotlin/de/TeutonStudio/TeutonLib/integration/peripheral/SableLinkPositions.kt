package de.TeutonStudio.TeutonLib.integration.peripheral

import de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

object SableLinkPositions {
    @JvmStatic
    fun world(level: ServerLevel, pos: BlockPos): Vec3 = SubLevelGeometry.projectToWorld(level, pos.center)
}
