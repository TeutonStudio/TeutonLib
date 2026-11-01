package de.TeutonStudio.TeutonLib.mixin.train

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers
import net.minecraft.world.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Pseudo
@Mixin(targets = ["createtrainfunctionality.TrainLinks"], remap = false)
abstract class CtfTrainLinksMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["isLinked(Lnet/minecraft/world/entity/Entity;)Z"], at = [At("HEAD")], cancellable = true, require = 1)
        private fun `teutonlib$noSecondaryCollider`(entity: Entity?, cir: CallbackInfoReturnable<Boolean>) {
            if (entity is CarriageContraptionEntity && !entity.level().isClientSide &&
                entity.carriage?.let { TrainPortalTransfers.link(it) } != null)
                cir.returnValue = true
        }
    }
}
