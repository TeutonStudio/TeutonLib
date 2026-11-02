package de.TeutonStudio.TeutonLib.mixin.hybrid.client

import de.TeutonStudio.TeutonLib.client.train.HybridTrainClient
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LightTexture
import org.joml.Matrix4f
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [LevelRenderer::class], priority = 900)
abstract class ContraptionLevelOutlineMixin {
    @Inject(method = ["renderLevel"], at = [At("TAIL")], require = 1)
    private fun `teutonlib$orangeFrame`(delta: DeltaTracker, outline: Boolean, camera: Camera,
        renderer: GameRenderer, light: LightTexture, modelView: Matrix4f, projection: Matrix4f, ci: CallbackInfo) {
        val minecraft = Minecraft.getInstance()
        if (!minecraft.entityRenderDispatcher.shouldRenderHitBoxes() || minecraft.showOnlyReducedInfo()) return
        val level = minecraft.level ?: return
        val buffers = minecraft.renderBuffers().bufferSource()
        for (sub in SubLevelContainer.getContainer(level)?.allSubLevels.orEmpty()) {
            HybridTrainClient.outline(sub, modelView, buffers, camera.position)
        }
        buffers.endLastBatch()
    }
}
