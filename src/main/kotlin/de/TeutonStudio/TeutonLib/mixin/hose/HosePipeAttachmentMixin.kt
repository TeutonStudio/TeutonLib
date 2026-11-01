package de.TeutonStudio.TeutonLib.mixin.hose

import de.TeutonStudio.TeutonLib.integration.hose.HosePipeGraph
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(targets = ["com.simibubi.create.content.fluids.pipes.FluidPipeBlock"], remap = false)
abstract class HosePipeAttachmentMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["canConnectTo"], at = [At("HEAD")], cancellable = true)
        private fun `teutonlib$reserveSocket`(world: BlockAndTintGetter, neighbourPos: BlockPos, state: BlockState,
            direction: Direction, cir: CallbackInfoReturnable<Boolean>) {
            if (HosePipeGraph.isVirtual(world, neighbourPos, direction.opposite)) cir.returnValue = false
        }
    }
}
