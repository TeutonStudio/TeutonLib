package de.TeutonStudio.TeutonLib.mixin.train

import com.llamalad7.mixinextras.sugar.Local
import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.TravellingPoint.IPortalListener
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalGuard
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyArg
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import de.TeutonStudio.TeutonLib.integration.train.CtfPortalAccess
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers

/** Null-world shunting is unchanged; world-aware portal entry uses the coordinated handoff. */
@Mixin(Carriage::class, remap = false)
abstract class CarriagePortalStopMixin {
    @Inject(method = ["anyAvailableEntity()Lcom/simibubi/create/content/trains/entity/CarriageContraptionEntity;"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$canonicalBuildOwner`(cir: CallbackInfoReturnable<CarriageContraptionEntity?>) {
        val link = TrainPortalTransfers.link(this as Any as Carriage) ?: return
        val entity = CtfPortalAccess.entity(link) as? CarriageContraptionEntity ?: return
        if (entity.isAlive) cir.returnValue = entity
    }

    @ModifyArg(method = ["travel(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/trains/graph/TrackGraph;DLcom/simibubi/create/content/trains/entity/TravellingPoint;Lcom/simibubi/create/content/trains/entity/TravellingPoint;I)D"],
        at = At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/TravellingPoint;travel(Lcom/simibubi/create/content/trains/graph/TrackGraph;DLcom/simibubi/create/content/trains/entity/TravellingPoint\$ITrackSelector;Lcom/simibubi/create/content/trains/entity/TravellingPoint\$IEdgePointListener;Lcom/simibubi/create/content/trains/entity/TravellingPoint\$ITurnListener;Lcom/simibubi/create/content/trains/entity/TravellingPoint\$IPortalListener;)D"),
        index = 5, require = 1)
    private fun `teutonlib$guardTrackPortal`(original: IPortalListener, @Local(argsOnly = true) level: Level?): IPortalListener =
        TrainPortalGuard.portalListener(level, this as Any as Carriage, original)
}
