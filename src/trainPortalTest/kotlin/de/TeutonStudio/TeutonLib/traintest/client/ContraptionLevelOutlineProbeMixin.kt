package de.TeutonStudio.TeutonLib.traintest.client.mixin

import de.TeutonStudio.TeutonLib.client.train.HybridTrainClient
import de.TeutonStudio.TeutonLib.traintest.client.TrainPortalClientVerification
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Observe the actual orange line submission in the disposable graphical test. */
@Mixin(HybridTrainClient::class, remap = false)
abstract class ContraptionLevelOutlineProbeMixin {
    @Inject(method = ["outline"], at = [At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDDDDFFFF)V")], require = 1)
    private fun `teutonlib$observeOutline`(sub: ClientSubLevel, matrix: Matrix4f, buffers: MultiBufferSource, camera: Vec3, ci: CallbackInfo) {
        TrainPortalClientVerification.orangeFrame()
    }
}
