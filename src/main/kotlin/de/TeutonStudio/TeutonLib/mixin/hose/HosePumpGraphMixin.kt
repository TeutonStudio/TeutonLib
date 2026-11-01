package de.TeutonStudio.TeutonLib.mixin.hose

import com.llamalad7.mixinextras.sugar.Local
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.simibubi.create.content.fluids.pump.PumpBlockEntity
import de.TeutonStudio.TeutonLib.integration.hose.HosePipeGraph
import net.createmod.catnip.math.BlockFace
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import com.simibubi.create.content.fluids.FluidTransportBehaviour
import net.minecraft.world.level.BlockGetter

@Mixin(PumpBlockEntity::class, remap = false)
abstract class HosePumpGraphMixin {
    @Inject(method = ["distributePressureTo"], at = [At("HEAD")], cancellable = true)
    private fun `teutonlib$sealVirtualIngress`(side: Direction, ci: CallbackInfo) {
        val pump = this as Any as BlockEntity
        if (HosePipeGraph.physicalEntryBlocked(pump.level, pump.blockPos, side)) ci.cancel()
    }

    @WrapOperation(method = ["distributePressureTo"], at = [At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/fluids/FluidPropagator;getPipe(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lcom/simibubi/create/content/fluids/FluidTransportBehaviour;", ordinal = 1)])
    private fun `teutonlib$sealPhysicalIngress`(world: BlockGetter, pos: BlockPos, original: Operation<FluidTransportBehaviour?>,
        @Local(ordinal = 1) blockFace: BlockFace): FluidTransportBehaviour? =
        if (HosePipeGraph.physicalEntryBlocked(world, blockFace.pos, blockFace.face)) null else original.call(world, pos)

    @WrapOperation(method = ["distributePressureTo", "hasReachedValidEndpoint"],
        at = [At(value = "INVOKE", target = "Lnet/createmod/catnip/math/BlockFace;getConnectedPos()Lnet/minecraft/core/BlockPos;")])
    private fun `teutonlib$remotePressure`(face: BlockFace, original: Operation<BlockPos>): BlockPos =
        HosePipeGraph.remote((this as Any as BlockEntity).level, face.pos, face.face) ?: original.call(face)

    @WrapOperation(method = ["searchForEndpointRecursively"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;", remap = true)])
    private fun `teutonlib$remoteEndpoint`(pos: BlockPos, side: Direction, original: Operation<BlockPos>): BlockPos =
        HosePipeGraph.remote((this as Any as BlockEntity).level, pos, side) ?: original.call(pos, side)
}
