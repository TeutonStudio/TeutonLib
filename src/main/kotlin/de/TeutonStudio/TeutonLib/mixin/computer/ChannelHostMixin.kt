package de.TeutonStudio.TeutonLib.mixin.computer

import de.TeutonStudio.TeutonLib.integration.computer.*
import de.TeutonStudio.TeutonLib.integration.computer.ChannelBank
import de.TeutonStudio.TeutonLib.integration.computer.ChannelHost
import de.TeutonStudio.TeutonLib.integration.computer.ChannelPersistence
import de.TeutonStudio.TeutonLib.integration.computer.ComputerIntegration
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(BlockEntity::class)
abstract class ChannelHostMixin : ChannelHost {
    @Unique
    private var `teutonlib$bank` = ChannelBank()
    override fun `teutonlib$channels`(): ChannelBank {
        return `teutonlib$bank`
    }

    @Inject(method = ["saveWithoutMetadata", "saveCustomOnly"], at = [At("RETURN")])
    private fun `teutonlib$save`(provider: HolderLookup.Provider, cir: CallbackInfoReturnable<CompoundTag>) {
        if (!`teutonlib$bank`.entries().isEmpty()) cir.returnValue!!
            .put("TeutonLibChannels", ChannelPersistence.save(`teutonlib$bank`))
    }

    @Inject(method = ["loadWithComponents", "loadCustomOnly"], at = [At("RETURN")])
    private fun `teutonlib$load`(tag: CompoundTag, provider: HolderLookup.Provider, ci: CallbackInfo?) {
        `teutonlib$bank` = ChannelPersistence.load(tag.getList("TeutonLibChannels", Tag.TAG_COMPOUND.toInt()))
        ComputerIntegration.loaded(this as Any as BlockEntity)
    }

    @Inject(method = ["setLevel"], at = [At("RETURN")])
    private fun `teutonlib$level`(level: Level, ci: CallbackInfo?) {
        ComputerIntegration.loaded(this as Any as BlockEntity)
    }

    @Inject(method = ["setRemoved"], at = [At("HEAD")])
    private fun `teutonlib$remove`(ci: CallbackInfo?) {
        ComputerIntegration.removed(this as Any as BlockEntity)
    }
}
