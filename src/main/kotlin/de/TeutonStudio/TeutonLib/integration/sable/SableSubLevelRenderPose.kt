package de.TeutonStudio.TeutonLib.integration.sable

import com.mojang.blaze3d.vertex.PoseStack
import de.TeutonStudio.TeutonLib.api.sable.SubLevelRenderPose
import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

internal object SableSubLevelRenderPose {
    fun apply(
        poseStack: PoseStack,
        blockEntity: BlockEntity,
        x: Double,
        y: Double,
        z: Double,
        camera: Vec3,
        partialTicks: Float
    ): SubLevelRenderPose.Result {
        val localPosition = Vec3(x, y, z)
        val subLevel = Sable.HELPER.getContainingClient(blockEntity)
        if (subLevel == null) {
            poseStack.translate(x - camera.x, y - camera.y, z - camera.z)
            return SubLevelRenderPose.Result(localPosition, false)
        }
        val renderPose = subLevel.renderPose(partialTicks)
        val worldPosition = renderPose.transformPosition(localPosition)
        poseStack.translate(worldPosition.x - camera.x, worldPosition.y - camera.y, worldPosition.z - camera.z)
        poseStack.mulPose(Quaternionf(renderPose.orientation()))
        return SubLevelRenderPose.Result(worldPosition, true)
    }
}
