package de.TeutonStudio.TeutonLib.mixin.hybrid

import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import net.minecraft.core.BlockPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(LevelChunk::class)
abstract class HybridChunkMixin {
    @Inject(method = ["setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$fixedTrainStructure`(pos: BlockPos, state: BlockState, moving: Boolean, cir: CallbackInfoReturnable<BlockState?>) {
        val chunk = this as Any as LevelChunk
        val level = chunk.level ?: return
        if (!HybridTrainRuntime.permits(level, pos, chunk.getBlockState(pos), state)) cir.returnValue = null
    }
}
