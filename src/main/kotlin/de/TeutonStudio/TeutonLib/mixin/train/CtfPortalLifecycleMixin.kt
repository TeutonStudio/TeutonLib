package de.TeutonStudio.TeutonLib.mixin.train

import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers
import net.minecraft.server.level.ServerLevel
import java.util.UUID
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import net.minecraft.world.entity.Entity

@Pseudo
@Mixin(targets = ["createtrainfunctionality.SubLevelLinks"], remap = false)
abstract class CtfPortalLifecycleMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["syncAll(Lnet/minecraft/server/level/ServerLevel;)V"], at = [At("HEAD")], require = 1)
        private fun `teutonlib$handoffBeforeFollow`(level: ServerLevel, ci: CallbackInfo) { TrainPortalTransfers.tick(level) }

        @JvmStatic
        @Inject(method = ["acrossByCreatesRecord(Lcreatetrainfunctionality/SubLevelLinks\$Link;)Lcom/simibubi/create/content/trains/entity/Carriage;"],
            at = [At("HEAD")], cancellable = true, require = 1)
        private fun `teutonlib$keepCrossingCargoLive`(link: SubLevelLinks.Link, cir: CallbackInfoReturnable<Carriage?>) {
            if (TrainPortalTransfers.manages(link)) cir.returnValue = null
        }

        @JvmStatic
        @Inject(method = ["aliveElsewhere(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)Lcom/simibubi/create/content/trains/entity/CarriageContraptionEntity;"],
            at = [At("HEAD")], cancellable = true, require = 1)
        private fun `teutonlib$ignorePortalViewCopy`(level: ServerLevel, id: UUID, cir: CallbackInfoReturnable<CarriageContraptionEntity?>) {
            val link = SubLevelLinks.linkFor(id)
            if (link != null && TrainPortalTransfers.manages(link)) cir.returnValue = null
        }

        @JvmStatic
        @Inject(method = ["buildIsElsewhere(Lnet/minecraft/world/entity/Entity;)Z"], at = [At("HEAD")], cancellable = true, require = 1)
        private fun `teutonlib$noDisassemblyOfViewCopy`(entity: Entity?, cir: CallbackInfoReturnable<Boolean>) {
            if (entity is CarriageContraptionEntity && !entity.level().isClientSide && TrainPortalTransfers.secondary(entity))
                cir.returnValue = true
        }
    }
}
