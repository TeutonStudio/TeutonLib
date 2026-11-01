package de.TeutonStudio.TeutonLib.mixin.train.client

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import createtrainfunctionality.ClientLinks
import net.minecraft.world.entity.Entity
import qouteall.imm_ptl.core.compat.mixin_create.IPCarriageAccessor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Pseudo
@Mixin(targets = ["createtrainfunctionality.TrainLinks"], remap = false)
abstract class CtfPortalRenderingMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["isLinked(Lnet/minecraft/world/entity/Entity;)Z"], at = [At("HEAD")], cancellable = true, require = 1)
        private fun `teutonlib$hideOtherPortalView`(entity: Entity?, cir: CallbackInfoReturnable<Boolean>) {
            if (entity !is CarriageContraptionEntity || !entity.level().isClientSide) return
            val carriage = entity.carriage ?: return
            if ((carriage as IPCarriageAccessor).ip_getEntities().values.any {
                it.entity.get()?.let { other -> ClientLinks.plotOriginOf(other.id) != null } == true
            }) cir.returnValue = true
        }
    }
}
