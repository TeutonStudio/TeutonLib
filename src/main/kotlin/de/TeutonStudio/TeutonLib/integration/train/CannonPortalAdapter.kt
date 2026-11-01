package de.TeutonStudio.TeutonLib.integration.train

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity

/** Loaded only for the verified CBC release. Native placement preserves loaded ammunition. */
internal object CannonPortalAdapter : PortalMachineAdapter {
    override val kind = "createbigcannons:mount"
    override fun matches(be: BlockEntity) = be is CannonMountBlockEntity || be is FixedCannonMountBlockEntity
    override fun running(be: BlockEntity) = when (be) {
        is CannonMountBlockEntity -> be.contraption?.isAlive == true
        is FixedCannonMountBlockEntity -> be.contraption?.isAlive == true
        else -> false
    }
    override fun stop(be: BlockEntity) { when (be) {
        is CannonMountBlockEntity -> be.disassemble()
        is FixedCannonMountBlockEntity -> be.disassemble()
    } }
    override fun resume(be: BlockEntity) { when (be) {
        is CannonMountBlockEntity -> be.onRedstoneUpdate(true, false, false, false, 0)
        is FixedCannonMountBlockEntity -> be.onRedstoneUpdate(true, false, false, false, 0)
    } }
    override fun saveState(be: BlockEntity): CompoundTag {
        val saved = be.saveWithoutMetadata(checkNotNull(be.level).registryAccess())
        return CompoundTag().also {
            it.putFloat("CannonYaw", saved.getFloat("CannonYaw"))
            it.putFloat("CannonPitch", saved.getFloat("CannonPitch"))
        }
    }
    override fun restoreState(be: BlockEntity, tag: CompoundTag) {
        val level = checkNotNull(be.level)
        val current = be.saveWithoutMetadata(level.registryAccess())
        current.putFloat("CannonYaw", tag.getFloat("CannonYaw"))
        current.putFloat("CannonPitch", tag.getFloat("CannonPitch"))
        be.loadWithComponents(current, level.registryAccess())
        be.setChanged()
    }
}
