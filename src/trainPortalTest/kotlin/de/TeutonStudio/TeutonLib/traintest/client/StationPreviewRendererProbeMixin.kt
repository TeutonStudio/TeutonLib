package de.TeutonStudio.TeutonLib.traintest.client.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.station.StationRenderer
import de.TeutonStudio.TeutonLib.traintest.client.StationPreviewClientVerification
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(StationRenderer::class)
abstract class StationPreviewRendererProbeMixin {
    @WrapOperation(method = ["renderSafe(Lcom/simibubi/create/content/trains/station/StationBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"],
        at = [At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/station/StationBlockEntity;isValidBogeyOffset(I)Z")], require = 1)
    private fun `teutonlib$previewPredicate`(station: StationBlockEntity, offset: Int, original: Operation<Boolean>): Boolean =
        original.call(station, offset).also { StationPreviewClientVerification.record(station, offset, it) }
}
