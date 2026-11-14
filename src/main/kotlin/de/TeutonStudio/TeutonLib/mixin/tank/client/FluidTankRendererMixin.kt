package de.TeutonStudio.TeutonLib.mixin.tank.client

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import com.simibubi.create.content.fluids.tank.FluidTankRenderer
import de.TeutonStudio.TeutonLib.client.fluid.SableTankFluidRenderer
import net.minecraft.client.renderer.MultiBufferSource
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(FluidTankRenderer::class, remap = false)
abstract class FluidTankRendererMixin {
    @Inject(
        method = ["renderSafe(Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"],
        at = [At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;getFluidLevel()Lnet/createmod/catnip/animation/LerpedFloat;"
        )],
        cancellable = true
    )
    private fun `teutonlib$levelFluidAgainstWorldGravity`(
        blockEntity: FluidTankBlockEntity,
        partialTicks: Float,
        pose: PoseStack,
        buffers: MultiBufferSource,
        light: Int,
        overlay: Int,
        ci: CallbackInfo
    ) {
        // This point follows Create's controller/window guards and leaves boiler rendering native.
        if (SableTankFluidRenderer.render(blockEntity, partialTicks, pose, buffers, light)) ci.cancel()
    }
}
