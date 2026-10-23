package de.TeutonStudio.TeutonLib.api.sable

import com.mojang.blaze3d.vertex.PoseStack
import de.TeutonStudio.TeutonLib.integration.sable.SableSubLevelRenderPose
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList

/** Client render-space projection for anchors which may live on a Sable sublevel. */
object SubLevelRenderPose {
    data class Result(val worldPosition: Vec3, val usedSubLevel: Boolean)

    @JvmStatic
    fun apply(
        poseStack: PoseStack,
        blockEntity: BlockEntity,
        x: Double,
        y: Double,
        z: Double,
        camera: Vec3,
        partialTicks: Float
    ): Result {
        if (runCatching { ModList.get().isLoaded("sable") }.getOrDefault(false)) {
            return SableSubLevelRenderPose.apply(poseStack, blockEntity, x, y, z, camera, partialTicks)
        }
        val worldPosition = Vec3(x, y, z)
        poseStack.translate(x - camera.x, y - camera.y, z - camera.z)
        return Result(worldPosition, false)
    }
}
