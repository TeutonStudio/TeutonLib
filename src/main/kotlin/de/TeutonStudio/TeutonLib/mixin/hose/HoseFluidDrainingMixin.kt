package de.TeutonStudio.TeutonLib.mixin.hose

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.simibubi.create.content.fluids.transfer.FluidDrainingBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import de.TeutonStudio.TeutonLib.integration.hose.HoseFluidTarget
import net.minecraft.core.BlockPos
import net.neoforged.neoforge.fluids.FluidStack
import org.spongepowered.asm.mixin.Mixin

@Mixin(FluidDrainingBehaviour::class, remap = false)
abstract class HoseFluidDrainingMixin {
    @WrapMethod(method = ["getDrainableFluid(Lnet/minecraft/core/BlockPos;)Lnet/neoforged/neoforge/fluids/FluidStack;"])
    private fun `teutonlib$physicalFluid`(position: BlockPos, original: Operation<FluidStack>): FluidStack {
        val target = HoseFluidTarget.resolve(this as Any as BlockEntityBehaviour, position) ?: return FluidStack.EMPTY
        return original.call(target)
    }

    @WrapMethod(method = ["pullNext(Lnet/minecraft/core/BlockPos;Z)Z"])
    private fun `teutonlib$physicalIntake`(position: BlockPos, simulate: Boolean, original: Operation<Boolean>): Boolean {
        val target = HoseFluidTarget.resolve(this as Any as BlockEntityBehaviour, position) ?: return false
        return original.call(target, simulate)
    }
}
