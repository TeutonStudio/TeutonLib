package de.TeutonStudio.TeutonLib.integration.hose

import de.TeutonStudio.TeutonLib.HoseHeadMode
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.sable.SableHoseOwnership
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

object HoseHeadPolicy {
    @JvmStatic
    fun enabled(blockEntity: BlockEntity): Boolean {
        val level = blockEntity.level ?: return false
        // The server sends this per-owner flag with the existing block-entity update packet.
        // Render transitions therefore do not depend on a live server-config resync to clients.
        if (level.isClientSide) return (blockEntity as? HosePulleyHost)?.physicalHeadEnabled == true
        return allowed(level, blockEntity.blockPos)
    }

    @JvmStatic
    fun allowed(level: Level, owner: BlockPos): Boolean {
        val mode = ServerConfig.hoseHeadMode()
        return mode.permits(mode == HoseHeadMode.SABLE_ONLY && SableHoseOwnership.isSubLevelOwner(level, owner))
    }
}
