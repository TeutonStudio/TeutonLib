package de.TeutonStudio.TeutonLib.mixin.flux

import de.TeutonStudio.TeutonLib.integration.energy.FluxPointConnections
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyVariable
import net.minecraft.world.level.block.state.BlockBehaviour
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(targets = ["sonar.fluxnetworks.common.block.FluxPointBlock"], remap = false)
abstract class FluxPointConnectionMixin {
    private companion object {
        @JvmStatic
        @ModifyVariable(method = ["<init>"], at = At("HEAD"), argsOnly = true)
        private fun `teutonlib$dynamicExtensionShapes`(properties: BlockBehaviour.Properties): BlockBehaviour.Properties = properties.dynamicShape()
    }

    @Inject(method = ["getShape"], at = [At("HEAD")], cancellable = true)
    private fun `teutonlib$connectionShape`(state: BlockState, level: BlockGetter, pos: BlockPos,
        context: CollisionContext, cir: CallbackInfoReturnable<VoxelShape>) {
        cir.returnValue = FluxPointConnections.shape(state,level,pos)
    }
}
