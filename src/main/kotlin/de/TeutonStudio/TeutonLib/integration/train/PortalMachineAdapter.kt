package de.TeutonStudio.TeutonLib.integration.train

import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity

internal interface PortalMachineAdapter {
    val kind: String
    fun matches(be: BlockEntity): Boolean
    fun running(be: BlockEntity): Boolean
    fun stop(be: BlockEntity)
    fun resume(be: BlockEntity)
    fun saveState(be: BlockEntity): CompoundTag = CompoundTag()
    fun restoreState(be: BlockEntity, tag: CompoundTag) {}
    fun child(be: BlockEntity): ServerSubLevel? = null
}
