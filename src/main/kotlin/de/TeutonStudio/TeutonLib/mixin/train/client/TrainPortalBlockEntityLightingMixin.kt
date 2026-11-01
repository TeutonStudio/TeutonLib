package de.TeutonStudio.TeutonLib.mixin.train.client

import com.mojang.blaze3d.vertex.PoseStack
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalClientLighting
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleRender
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyArg

@Mixin(BlockEntityRenderDispatcher::class)
abstract class TrainPortalBlockEntityLightingMixin {
    private companion object {
        @JvmStatic
        @ModifyArg(method = ["setupAndRender"], at = At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"),
            index = 4, require = 1)
        private fun `teutonlib$lightMovingMachinery`(block: BlockEntity, partialTick: Float,
            pose: PoseStack, buffers: MultiBufferSource, original: Int, overlay: Int): Int {
            val ship = Sable.HELPER.getContaining(block) as? ClientSubLevel ?: return original
            if (TrainPortalClientLighting.skyLightScale(ship) == null) return original
            // Machinery may occupy an opaque block. Its moving parts need the light
            // at the exposed faces as well as the centre, as vanilla block faces do.
            fun kept(pos: net.minecraft.core.BlockPos) = IPStraddleRender.keepsBlockEntity(ship, pos) &&
                IPStraddleRender.keepsInPortalPass(ship, pos)
            var light = if (kept(block.blockPos))
                TrainPortalClientLighting.samplePackedLight(ship, Vec3.atCenterOf(block.blockPos)) else 0
            for (face in Direction.values()) {
                val pos = block.blockPos.relative(face)
                if (!kept(pos)) continue
                val faceLight = TrainPortalClientLighting.samplePackedLight(ship, Vec3.atCenterOf(pos))
                light = maxOf(light and 0xffff, faceLight and 0xffff) or
                    (maxOf(light ushr 16, faceLight ushr 16) shl 16)
            }
            return light
        }
    }
}
