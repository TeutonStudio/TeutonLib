package de.TeutonStudio.TeutonLib.mixin.hose.client

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.content.contraptions.pulley.AbstractPulleyRenderer
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadPolicy
import net.minecraft.client.renderer.MultiBufferSource
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Keep Create's shaft and animated coil, then replace only its hanging hose and magnet. */
@Mixin(AbstractPulleyRenderer::class, remap = false)
abstract class AbstractPulleyRendererMixin {
    @Inject(
        method = ["renderSafe(Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/createmod/catnip/render/SuperByteBuffer;renderInto(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )],
        cancellable = true
    )
    private fun `teutonlib$replaceNativeHose`(
        blockEntity: KineticBlockEntity,
        partialTicks: Float,
        pose: PoseStack,
        buffers: MultiBufferSource,
        light: Int,
        overlay: Int,
        ci: CallbackInfo
    ) {
        if (blockEntity is HosePulleyBlockEntity && HoseHeadPolicy.enabled(blockEntity)) ci.cancel()
    }
}
