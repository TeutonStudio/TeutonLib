package de.TeutonStudio.TeutonLib.mixin.hose

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import de.TeutonStudio.TeutonLib.integration.hose.HosePipeGraph
import de.TeutonStudio.TeutonLib.integration.hose.HoseDrainConnection
import com.simibubi.create.content.fluids.FluidTransportBehaviour
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Higher priority also reaches TFMG's merged fluid propagator overwrite. */
@Mixin(targets = ["com.simibubi.create.content.fluids.FluidPropagator"], priority = 1200, remap = false)
abstract class HosePropagatorGraphMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["getPipe"], at = [At("RETURN")], cancellable = true)
        private fun `teutonlib$ordinaryDrain`(world: BlockGetter, pos: BlockPos, cir: CallbackInfoReturnable<FluidTransportBehaviour?>) {
            val connection = cir.returnValue as? HoseDrainConnection ?: return
            if (connection.headId == null) cir.returnValue = null
        }

        @JvmStatic
        @WrapOperation(method = ["propagateChangedPipe"], at = [At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;", remap = true)])
        private fun `teutonlib$changedEdge`(pos: BlockPos, side: Direction, original: Operation<BlockPos>,
            world: LevelAccessor, start: BlockPos, state: net.minecraft.world.level.block.state.BlockState): BlockPos =
            HosePipeGraph.remote(world, pos, side) ?: original.call(pos, side)

        @JvmStatic
        @WrapOperation(method = ["resetAffectedFluidNetworks"], at = [At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;", remap = true)])
        private fun `teutonlib$resetEdge`(pos: BlockPos, side: Direction, original: Operation<BlockPos>,
            world: Level, start: BlockPos, startSide: Direction): BlockPos =
            HosePipeGraph.remote(world, pos, side) ?: original.call(pos, side)

        @JvmStatic
        @Inject(method = ["isOpenEnd"], at = [At("HEAD")], cancellable = true)
        private fun `teutonlib$closedSocket`(world: BlockGetter, pos: BlockPos, side: Direction, cir: CallbackInfoReturnable<Boolean>) {
            if (HosePipeGraph.isVirtual(world, pos, side)) cir.returnValue = false
        }

        @JvmStatic
        @Inject(method = ["hasFluidCapability"], at = [At("HEAD")], cancellable = true)
        private fun `teutonlib$throughPipe`(world: BlockGetter, pos: BlockPos, side: Direction, cir: CallbackInfoReturnable<Boolean>) {
            // A docked pulley is a pipe node, not its old world-fluid endpoint.
            if (HosePipeGraph.connector(world, pos) != null) cir.returnValue = false
        }
    }
}
