package de.TeutonStudio.TeutonLib.mixin.computer

import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import de.TeutonStudio.TeutonLib.integration.computer.*
import de.TeutonStudio.TeutonLib.integration.computer.ChannelHost
import de.TeutonStudio.TeutonLib.integration.computer.ChannelPersistence
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [ComputerBlockEntity::class], remap = false)
abstract class ComputerChannelSyncMixin {
    @Inject(method = ["getUpdateTag"], at = [At("RETURN")])
    private fun `teutonlib$update`(provider: HolderLookup.Provider, cir: CallbackInfoReturnable<CompoundTag>) {
        cir.returnValue!!
            .put("TeutonLibChannels", ChannelPersistence.save((this as ChannelHost).`teutonlib$channels`()))
        cir.returnValue!!
            .put("TeutonLibSourceNicknames", SourceNicknamePersistence.save((this as SourceNicknameHost).`teutonlib$sourceNicknames`()))
    }
}
