package de.TeutonStudio.TeutonLib.mekatest.client.mixin

import de.TeutonStudio.TeutonLib.mekatest.client.MekanismModelProbe
import mekanism.common.tile.transmitter.TileEntityTransmitter
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(targets = ["mekanism.common.tile.transmitter.TileEntityTransmitter"], remap = false)
abstract class MekanismTileUpdateProbeMixin {
    @Inject(method = ["handleUpdateTag(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup\$Provider;)V"], at = [At("RETURN")])
    private fun teutonlibTestAppliedUpdate(tag: CompoundTag, provider: HolderLookup.Provider, ci: CallbackInfo) {
        MekanismModelProbe.recordTileUpdate(this as Any as TileEntityTransmitter, tag)
    }
}
