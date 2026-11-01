package de.TeutonStudio.TeutonLib.mixin.fluids

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.sugar.Local
import com.llamalad7.mixinextras.sugar.Share
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef
import com.llamalad7.mixinextras.sugar.ref.LocalRef
import com.simibubi.create.content.fluids.FluidTransportBehaviour
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidPumpNetworkUpdater
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidPumpNetworkUpdater.PropagationContext
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidPumpBlock
import net.createmod.catnip.data.Pair
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Injection points may enter TFMG's merged overwrite only with a higher priority than its 1000. */
@Mixin(targets = ["com.simibubi.create.content.fluids.FluidPropagator"], priority = 1100, remap = false)
abstract class TfmgFluidPropagatorMixin {
    private companion object {
        // TFMG's hard-coded pump list otherwise skips FluidLogistics pumps when pressure carries
        // the native traversal past Create's cutoff, so the extended-range frontier stays empty.
        @JvmStatic
        @ModifyExpressionValue(method = ["propagateChangedPipe(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"],
            at = [At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 0)], require = 1)
        private fun `teutonlib$includeFluidPump`(
            original: Boolean, world: LevelAccessor, pos: BlockPos, state: BlockState,
            @Local(ordinal = 2) targetState: BlockState
        ): Boolean = original || targetState.block is FluidPumpBlock

        @JvmStatic
        @Inject(method = ["propagateChangedPipe(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"],
            at = [At(value = "INVOKE", target = "Lcom/simibubi/create/content/fluids/FluidTransportBehaviour;wipePressure()V")], require = 1)
        private fun `teutonlib$recordTraversal`(
            world: LevelAccessor, pos: BlockPos, state: BlockState, ci: CallbackInfo,
            @Local pair: Pair<Int, BlockPos>, @Local pipe: FluidTransportBehaviour,
            @Share("teutonlibFluidPropagation") context: LocalRef<PropagationContext?>,
            @Share("teutonlibFluidPropagationInitialized") initialized: LocalBooleanRef
        ) {
            if (!initialized.get()) {
                context.set(FluidPumpNetworkUpdater.createContext(world))
                initialized.set(true)
            }
            context.get()?.recordCreatePipe(pair.second, pair.first, pipe)
        }

        @JvmStatic
        @ModifyExpressionValue(method = ["propagateChangedPipe(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"],
            at = [At(value = "INVOKE", target = "Lcom/simibubi/create/content/fluids/FluidTransportBehaviour;hasAnyPressure()Z")], require = 1)
        private fun `teutonlib$captureFrontier`(
            original: Boolean, world: LevelAccessor, pos: BlockPos, state: BlockState,
            @Local(ordinal = 2) target: BlockPos, @Local(ordinal = 2) targetState: BlockState,
            @Local(ordinal = 1) targetPipe: FluidTransportBehaviour,
            @Local distance: Int, @Local direction: Direction,
            @Share("teutonlibFluidPropagation") context: LocalRef<PropagationContext?>
        ): Boolean {
            if (!original) {
                val propagation = context.get()
                if (propagation != null && propagation.shouldContinueFromCreateCutoff(distance) &&
                    targetPipe.canHaveFlowToward(targetState, direction.opposite)) {
                    propagation.addCutoff(target, distance + 1)
                }
            }
            return original
        }

        @JvmStatic
        @Inject(method = ["propagateChangedPipe(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"],
            at = [At("TAIL")], require = 1)
        private fun `teutonlib$finishPropagation`(
            world: LevelAccessor, pos: BlockPos, state: BlockState, ci: CallbackInfo,
            @Share("teutonlibFluidPropagation") context: LocalRef<PropagationContext?>
        ) {
            FluidPumpNetworkUpdater.finishPropagationForFluidPumps(world, context.get())
        }
    }
}
