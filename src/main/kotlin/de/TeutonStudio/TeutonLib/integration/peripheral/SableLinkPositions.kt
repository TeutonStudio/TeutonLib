package de.TeutonStudio.TeutonLib.integration.peripheral

import dev.ryanhcode.sable.Sable
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

object SableLinkPositions {
    @JvmStatic
    fun world(level: ServerLevel, pos: BlockPos): Vec3 {
        val sublevel = Sable.HELPER.getContaining(level, pos)
        return if (sublevel == null) pos.center else sublevel.logicalPose().transformPosition(pos.center)
    }
}
