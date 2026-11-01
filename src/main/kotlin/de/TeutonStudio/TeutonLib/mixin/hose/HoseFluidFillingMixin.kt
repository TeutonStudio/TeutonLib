package de.TeutonStudio.TeutonLib.mixin.hose

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.simibubi.create.content.fluids.transfer.FluidFillingBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import de.TeutonStudio.TeutonLib.integration.hose.HoseFluidTarget
import net.minecraft.core.BlockPos
import net.minecraft.world.level.material.Fluid
import org.spongepowered.asm.mixin.Mixin

@Mixin(FluidFillingBehaviour::class, remap = false)
abstract class HoseFluidFillingMixin {
    @WrapMethod(method = ["tryDeposit(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/core/BlockPos;Z)Z"])
    private fun `teutonlib$physicalOutlet`(fluid: Fluid, position: BlockPos, simulate: Boolean,
        original: Operation<Boolean>): Boolean {
        val target = HoseFluidTarget.resolve(this as Any as BlockEntityBehaviour, position) ?: return false
        return original.call(fluid, target, simulate)
    }
}
