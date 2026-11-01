package de.TeutonStudio.TeutonLib.traintest.client.mixin

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import de.TeutonStudio.TeutonLib.traintest.client.TrainPortalClientVerification
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Observes final world endpoints after portal clipping, without issuing a draw itself. */
@Mixin(targets = ["de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalCouplings"], remap = false)
abstract class TrainPortalCouplingProbeMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["drawSegment"], at = [At("HEAD")], remap = false)
        private fun teutonlibTestCoupling(pose: PoseStack, consumer: VertexConsumer, camera: Vec3,
            first: Vec3, second: Vec3, firstLight: Int, secondLight: Int,
            firstHead: Boolean, secondHead: Boolean, ci: CallbackInfo) {
            TrainPortalClientVerification.recordCoupling(first, second, firstLight, secondLight, firstHead, secondHead)
        }
    }
}
