package de.TeutonStudio.TeutonLib.hosetest.client.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.content.contraptions.pulley.AbstractPulleyRenderer
import de.TeutonStudio.TeutonLib.hosetest.client.HosePulleyClientVerification
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.LevelAccessor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/** Observe native hanging-hose draws after TeutonLib's selective cancellation point. */
@Mixin(AbstractPulleyRenderer::class, remap = false)
abstract class HoseNativeRenderProbeMixin {
    @WrapOperation(method = ["renderSafe(Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"],
        at = [At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/pulley/AbstractPulleyRenderer;renderAt(Lnet/minecraft/world/level/LevelAccessor;Lnet/createmod/catnip/render/SuperByteBuffer;FLnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V")])
    private fun teutonlibTestNativeDraw(level: LevelAccessor, buffer: SuperByteBuffer, offset: Float,
        position: BlockPos, pose: PoseStack, vertices: VertexConsumer, original: Operation<Void>) {
        HosePulleyClientVerification.recordNativeDraw(position)
        original.call(level, buffer, offset, position, pose, vertices)
    }
}
