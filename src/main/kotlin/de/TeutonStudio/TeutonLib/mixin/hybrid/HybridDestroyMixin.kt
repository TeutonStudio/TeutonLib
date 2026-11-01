package de.TeutonStudio.TeutonLib.mixin.hybrid

import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Level::class)
abstract class HybridDestroyMixin {
    /** Refuse before destroyBlock emits drops, particles or block events. */
    @Inject(method = ["destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$noDropsFromLockedTrain`(pos: BlockPos, drops: Boolean, entity: Entity?, depth: Int, cir: CallbackInfoReturnable<Boolean>) {
        val level = this as Any as Level
        if (!HybridTrainRuntime.permits(level, pos, level.getBlockState(pos), Blocks.AIR.defaultBlockState())) cir.returnValue = false
    }
}
