package de.TeutonStudio.TeutonLib.hosetest.client.mixin

import com.mojang.blaze3d.vertex.PoseStack
import de.TeutonStudio.TeutonLib.client.hose.HoseHeadRenderer
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import de.TeutonStudio.TeutonLib.hosetest.client.HosePulleyClientVerification
import net.minecraft.client.renderer.MultiBufferSource
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Development-only observer for an actual render invocation; never draws the model itself. */
@Mixin(HoseHeadRenderer::class, remap = false)
abstract class HoseHeadRenderProbeMixin {
    @Inject(
        method = ["render(Lde/TeutonStudio/TeutonLib/integration/hose/HoseHeadEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"],
        at = [At("HEAD")]
    )
    private fun `teutonlib$recordHoseDraw`(
        entity: HoseHeadEntity, yaw: Float, partialTicks: Float,
        pose: PoseStack, buffers: MultiBufferSource, light: Int, ci: CallbackInfo
    ) {
        HosePulleyClientVerification.recordRender(entity)
    }
}
