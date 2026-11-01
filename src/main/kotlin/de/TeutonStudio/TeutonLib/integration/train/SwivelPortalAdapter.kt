package de.TeutonStudio.TeutonLib.integration.train

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity

internal object SwivelPortalAdapter : PortalMachineAdapter {
    override val kind = "simulated:swivel"
    override fun matches(be: BlockEntity) = be is SwivelBearingBlockEntity
    override fun running(be: BlockEntity) = (be as SwivelBearingBlockEntity).isAssembled && be.subLevelID != null
    override fun stop(be: BlockEntity) = (be as SwivelBearingBlockEntity).disassemble()
    override fun resume(be: BlockEntity) = (be as SwivelBearingBlockEntity).assemble()
    override fun child(be: BlockEntity): ServerSubLevel? {
        val bearing = be as SwivelBearingBlockEntity
        return bearing.subLevelID?.let { SubLevelContainer.getContainer(checkNotNull(be.level))?.getSubLevel(it) as? ServerSubLevel }
    }
    override fun saveState(be: BlockEntity) = CompoundTag().also {
        it.putDouble("TargetAngle", (be as SwivelBearingBlockEntity).targetAngleDegrees)
    }
    override fun restoreState(be: BlockEntity, tag: CompoundTag) {
        val level = checkNotNull(be.level)
        val current = be.saveWithoutMetadata(level.registryAccess())
        current.putDouble("TargetAngle", tag.getDouble("TargetAngle"))
        be.loadWithComponents(current, level.registryAccess()); be.setChanged()
    }
}
