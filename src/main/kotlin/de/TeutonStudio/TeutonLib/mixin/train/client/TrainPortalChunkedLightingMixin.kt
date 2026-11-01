package de.TeutonStudio.TeutonLib.mixin.train.client

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.mojang.blaze3d.vertex.VertexBuffer
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalClientLighting
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.Camera
import net.minecraft.client.PrioritizeChunkUpdates
import net.minecraft.client.renderer.chunk.RenderRegionCache
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [VanillaChunkedSubLevelRenderData::class], remap = false)
abstract class TrainPortalChunkedLightingMixin {
    @Shadow @Final private lateinit var subLevel: ClientSubLevel
    @Shadow @Final private lateinit var sectionRenderDispatcher: SectionRenderDispatcher
    @Shadow abstract fun allRenderSections(): it.unimi.dsi.fastutil.objects.ObjectList<SectionRenderDispatcher.RenderSection>

    @WrapMethod(method = ["compileSections(Lnet/minecraft/client/PrioritizeChunkUpdates;Lnet/minecraft/client/renderer/chunk/RenderRegionCache;Lnet/minecraft/client/Camera;)V"])
    private fun `teutonlib$scopeCompileWorld`(priority: PrioritizeChunkUpdates, cache: RenderRegionCache,
        camera: Camera, original: Operation<Void>) {
        val previous = (sectionRenderDispatcher as TrainPortalSectionDispatcherAccess).`teutonlib$getCompileLevel`()
        try {
            // IP's existing HEAD injection selects the build's source world. A
            // remote build may share the main world's dispatcher, so its choice
            // must not leak into the main renderer's next terrain compilation.
            original.call(priority, cache, camera)
        } finally {
            sectionRenderDispatcher.setLevel(previous)
        }
    }

    // resize runs in the constructor, before createRenderData schedules any mesh
    // upload. Registration therefore precedes the very first VertexBuffer.upload.
    @Inject(method = ["resize"], at = [At("TAIL")], require = 1)
    private fun `teutonlib$registerPlotBuffers`(ci: CallbackInfo) {
        for (section in allRenderSections()) for (layer in RenderType.chunkBufferLayers()) {
            TrainPortalClientLighting.register(subLevel, section.origin, section.getBuffer(layer))
        }
        TrainPortalClientLighting.invalidate(subLevel)
    }

    @Inject(method = ["setDirty"], at = [At("HEAD")], require = 1)
    private fun `teutonlib$invalidatePlotLight`(x: Int, y: Int, z: Int, playerChanged: Boolean, ci: CallbackInfo) {
        TrainPortalClientLighting.invalidate(subLevel)
    }

    @ModifyExpressionValue(method = ["renderChunkedSubLevel"], at = [At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/sublevel/ClientSubLevel;getLatestSkyLightScale()I")], require = 1)
    private fun `teutonlib$useRelitSky`(original: Int): Int = TrainPortalClientLighting.skyLightScale(subLevel) ?: original

    @Redirect(method = ["renderChunkedSubLevel"], at = At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"), require = 1)
    private fun `teutonlib$relightPortalHalf`(buffer: VertexBuffer) {
        TrainPortalClientLighting.beforeDraw(buffer)
        buffer.draw()
    }
}
