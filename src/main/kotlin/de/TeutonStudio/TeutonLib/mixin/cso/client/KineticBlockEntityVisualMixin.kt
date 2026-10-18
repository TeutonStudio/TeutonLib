package de.TeutonStudio.TeutonLib.mixin.cso.client

import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual
import de.TeutonStudio.TeutonLib.integration.cso.bridge.ClientGearBridgeManager
import de.TeutonStudio.TeutonLib.client.CsoClient
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [KineticBlockEntityVisual::class], remap = false)
abstract class KineticBlockEntityVisualMixin {
    private companion object {
        @JvmStatic @Inject(method = ["rotationOffset"], at = [At("RETURN")], cancellable = true, remap = false)
        private fun cso_useGlobalCogPhase(state: BlockState, axis: Direction.Axis, position: Vec3i, cir: CallbackInfoReturnable<Float>) {
            if (!CsoClient.enabled()) return
            ClientGearBridgeManager.phaseCorrection(position).takeIf { it != 0f }?.let { cir.returnValue = cir.returnValue + it }
        }
    }
}
