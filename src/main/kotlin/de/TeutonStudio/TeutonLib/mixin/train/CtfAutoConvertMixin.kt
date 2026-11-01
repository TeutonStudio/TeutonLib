package de.TeutonStudio.TeutonLib.mixin.train

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import createtrainfunctionality.TrainConfig
import de.TeutonStudio.TeutonLib.integration.train.CtfPortalAccess
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers
import net.minecraft.server.level.ServerLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Pseudo
@Mixin(targets = ["createtrainfunctionality.AutoConvert"], remap = false)
abstract class CtfAutoConvertMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["consider(Lcom/simibubi/create/content/trains/entity/CarriageContraptionEntity;Lcreatetrainfunctionality/TrainConfig\$Handling;)V"],
            at = [At("HEAD")], cancellable = true, require = 1)
        private fun `teutonlib$oneBuildPerCarriage`(entity: CarriageContraptionEntity, handling: TrainConfig.Handling, ci: CallbackInfo) {
            if (TrainPortalTransfers.secondary(entity)) {
                (entity.level() as? ServerLevel)?.let { CtfPortalAccess.unregister(it, entity) }
                ci.cancel()
            }
        }
    }
}
