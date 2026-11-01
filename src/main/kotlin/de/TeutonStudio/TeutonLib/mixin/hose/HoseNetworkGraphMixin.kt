package de.TeutonStudio.TeutonLib.mixin.hose

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import com.simibubi.create.content.fluids.FluidNetwork
import com.simibubi.create.content.fluids.FlowSource
import de.TeutonStudio.TeutonLib.integration.hose.HoseDrainConnection
import de.TeutonStudio.TeutonLib.integration.hose.HoseNetworkFlow
import de.TeutonStudio.TeutonLib.integration.hose.HosePipeGraph
import net.createmod.catnip.data.Pair
import net.createmod.catnip.math.BlockFace
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(FluidNetwork::class, remap = false)
abstract class HoseNetworkGraphMixin {
    @Shadow lateinit var world: Level
    @Unique private var `teutonlib$pathStorage`: HashMap<BlockPos, List<HoseDrainConnection>>? = null
    @Unique private fun `teutonlib$paths`(): HashMap<BlockPos, List<HoseDrainConnection>> {
        return `teutonlib$pathStorage` ?: HashMap<BlockPos, List<HoseDrainConnection>>().also { `teutonlib$pathStorage` = it }
    }
    @Unique private val `teutonlib$transfer` = HoseNetworkFlow()

    @Inject(method = ["reset"], at = [At("HEAD")])
    private fun `teutonlib$resetPaths`(ci: CallbackInfo) { `teutonlib$pathStorage`?.clear() }

    @Inject(method = ["tick"], at = [At("HEAD")], cancellable = true)
    private fun `teutonlib$validatePaths`(ci: CallbackInfo) {
        `teutonlib$transfer`.clear()
        if (`teutonlib$paths`().values.any { path -> path.any { !it.active() } }) {
            (this as Any as FluidNetwork).reset()
            ci.cancel()
        }
    }

    @WrapOperation(method = ["tick"], at = [At(value = "INVOKE",
        target = "Lnet/createmod/catnip/math/BlockFace;getConnectedPos()Lnet/minecraft/core/BlockPos;")])
    private fun `teutonlib$connected`(face: BlockFace, original: Operation<BlockPos>): BlockPos =
        HosePipeGraph.remote(world, face.pos, face.face) ?: original.call(face)

    @WrapOperation(method = ["tick"], at = [At(value = "INVOKE",
        target = "Lnet/createmod/catnip/math/BlockFace;getOpposite()Lnet/createmod/catnip/math/BlockFace;")])
    private fun `teutonlib$opposite`(face: BlockFace, original: Operation<BlockFace>): BlockFace {
        val remote = HosePipeGraph.remote(world, face.pos, face.face)
        val result = if (remote != null) BlockFace(remote, face.oppositeFace) else original.call(face)
        val path = `teutonlib$paths`()[face.pos].orEmpty()
        val connector = if (remote != null) HosePipeGraph.connector(world, face.pos) else null
        `teutonlib$paths`().putIfAbsent(result.pos, if (connector != null) (path + connector).distinct() else path)
        return result
    }

    @WrapOperation(method = ["tick"], at = [At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/fluids/capability/IFluidHandler;fill(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler\$FluidAction;)I")])
    private fun `teutonlib$halfFlow`(handler: IFluidHandler, fluid: FluidStack, action: FluidAction, original: Operation<Int>,
        @Local pair: Pair<BlockFace, FlowSource>): Int {
        val path = `teutonlib$paths`()[pair.first.pos].orEmpty()
        return if (path.isEmpty()) original.call(handler, fluid, action) else `teutonlib$transfer`.fill(world, path, handler, fluid, action)
    }
}
