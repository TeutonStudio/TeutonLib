package de.TeutonStudio.TeutonLib.mixin.cso

import com.simibubi.create.content.kinetics.RotationPropagator
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import de.TeutonStudio.TeutonLib.integration.cso.bridge.BridgeRegistry
import de.TeutonStudio.TeutonLib.integration.CsoRuntime
import net.minecraft.core.BlockPos
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [RotationPropagator::class], priority = 700)
abstract class RotationPropagatorMixin {
    private companion object {
        @JvmStatic @Inject(method = ["getRotationSpeedModifier"], at = [At("HEAD")], cancellable = true, remap = false)
        private fun cso_virtualCogRatio(from: KineticBlockEntity, to: KineticBlockEntity, cir: CallbackInfoReturnable<Float>) { if (!CsoRuntime.enabled()) return; BridgeRegistry.modifier(from, to)?.let { cir.returnValue = it } }
        @JvmStatic @Inject(method = ["getConnectedNeighbours"], at = [At("RETURN")], cancellable = true, remap = false)
        private fun cso_appendVirtualCogNeighbours(be: KineticBlockEntity, cir: CallbackInfoReturnable<MutableList<KineticBlockEntity>>) {
            if (!CsoRuntime.enabled()) return
            val result = ArrayList(cir.returnValue); BridgeRegistry.neighbours(be).filter { !it.isRemoved && it !in result }.forEach(result::add); cir.returnValue = result
        }
        @JvmStatic @Inject(method = ["getPotentialNeighbourLocations"], at = [At("RETURN")], cancellable = true, remap = false)
        private fun cso_appendVirtualCogPositions(be: KineticBlockEntity, cir: CallbackInfoReturnable<MutableList<BlockPos>>) {
            if (!CsoRuntime.enabled()) return
            val result = ArrayList(cir.returnValue); BridgeRegistry.neighbours(be).map { it.blockPos }.filter { it !in result }.forEach(result::add); cir.returnValue = result
        }
    }
}
