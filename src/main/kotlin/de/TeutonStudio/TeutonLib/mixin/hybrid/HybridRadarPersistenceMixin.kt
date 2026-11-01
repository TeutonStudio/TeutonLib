package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.simibubi.create.content.contraptions.Contraption
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridRadarData
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Contraption::class)
abstract class HybridRadarPersistenceMixin {
    @Inject(method = ["writeNBT(Lnet/minecraft/core/HolderLookup\$Provider;Z)Lnet/minecraft/nbt/CompoundTag;"],
        at = [At("RETURN")], require = 1)
    private fun `teutonlib$saveRadar`(registries: HolderLookup.Provider, spawn: Boolean, cir: CallbackInfoReturnable<CompoundTag>) {
        val data = (this as Any) as? HybridRadarData ?: return
        val tag = CompoundTag()
        tag.putInt("Dishes", data.`teutonlib$getDishCount`())
        tag.putBoolean("Receiver", data.`teutonlib$getHasReceiver`())
        tag.putBoolean("Creative", data.`teutonlib$getCreative`())
        data.`teutonlib$getReceiverFacing`() ?.let { tag.putString("Facing", it.serializedName) }
        cir.returnValue.put("teutonlib:radar", tag)
    }

    @Inject(method = ["readNBT(Lnet/minecraft/world/level/Level;Lnet/minecraft/nbt/CompoundTag;Z)V"],
        at = [At("RETURN")], require = 1)
    private fun `teutonlib$loadRadar`(level: Level, tag: CompoundTag, spawn: Boolean, ci: CallbackInfo) {
        val data = (this as Any) as? HybridRadarData ?: return
        if (!tag.contains("teutonlib:radar")) return
        val saved = tag.getCompound("teutonlib:radar")
        data.`teutonlib$setDishCount`(saved.getInt("Dishes"))
        data.`teutonlib$setHasReceiver`(saved.getBoolean("Receiver"))
        data.`teutonlib$setCreative`(saved.getBoolean("Creative"))
        data.`teutonlib$setReceiverFacing`(Direction.byName(saved.getString("Facing")))
    }
}
