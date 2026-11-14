package de.TeutonStudio.TeutonLib.mixin.flux

import de.TeutonStudio.TeutonLib.integration.energy.FluxPointConnections
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(targets = ["sonar.fluxnetworks.common.device.TileFluxDevice"], remap = false)
abstract class FluxPointTickMixin {
    @Inject(method = ["onServerTick"], at = [At("TAIL")])
    private fun `teutonlib$rearConnections`(ci: CallbackInfo) {
        val entity = this as Any as BlockEntity
        val level = entity.level ?: return
        if (FluxPointConnections.isPoint(entity.blockState.block)) FluxPointConnections.reconcile(level, entity.blockPos)
    }
}
