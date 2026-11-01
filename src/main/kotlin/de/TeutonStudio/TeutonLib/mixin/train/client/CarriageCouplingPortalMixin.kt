package de.TeutonStudio.TeutonLib.mixin.train.client

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.CarriageCouplingRenderer
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalCouplings
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(CarriageCouplingRenderer::class, remap = false)
abstract class CarriageCouplingPortalMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["renderAll(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;)V"],
            at = [At("HEAD")], require = 1)
        private fun `teutonlib$drawPortalCouplings`(pose: PoseStack, buffers: MultiBufferSource, camera: Vec3, ci: CallbackInfo) {
            TrainPortalCouplings.render(pose, buffers, camera)
        }

        @JvmStatic
        @Inject(method = ["renderAll(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;)V"],
            at = [At("RETURN")], require = 1)
        private fun `teutonlib$releaseCouplingPass`(pose: PoseStack, buffers: MultiBufferSource, camera: Vec3, ci: CallbackInfo) {
            TrainPortalCouplings.endRender()
        }

        @JvmStatic
        @WrapOperation(method = ["renderAll(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;)V"],
            at = [At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Carriage;getDimensional(Lnet/minecraft/world/level/Level;)Lcom/simibubi/create/content/trains/entity/Carriage\$DimensionalCarriageEntity;")], require = 2)
        private fun `teutonlib$replaceManagedPair`(carriage: Carriage, level: Level,
            original: Operation<Carriage.DimensionalCarriageEntity>): Carriage.DimensionalCarriageEntity =
            if (TrainPortalCouplings.manages(carriage)) carriage.DimensionalCarriageEntity() else original.call(carriage, level)
    }
}
