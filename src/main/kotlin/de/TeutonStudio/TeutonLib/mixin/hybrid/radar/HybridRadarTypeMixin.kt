package de.TeutonStudio.TeutonLib.mixin.hybrid.radar

import com.happysg.radar.block.radar.bearing.RadarContraption
import com.simibubi.create.api.contraption.ContraptionType
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridRadarTypes
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(RadarContraption::class, remap = false)
abstract class HybridRadarTypeMixin {
    @Inject(method = ["getType()Lcom/simibubi/create/api/contraption/ContraptionType;"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$keepRadarType`(cir: CallbackInfoReturnable<ContraptionType>) {
        cir.returnValue = HybridRadarTypes.RADAR.get()
    }
}
