package de.TeutonStudio.TeutonLib.mixin.train.client

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexBuffer
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalClientLighting
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalVertexBufferAccess
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.nio.ByteBuffer

@Mixin(VertexBuffer::class)
abstract class TrainPortalVertexBufferLightingMixin : TrainPortalVertexBufferAccess {
    @Shadow private var vertexBufferId = 0

    @Inject(method = ["upload"], at = [At("HEAD")], require = 1)
    private fun `teutonlib$capturePlotLighting`(data: MeshData, ci: CallbackInfo) {
        TrainPortalClientLighting.capture(this as VertexBuffer, data)
    }

    @Inject(method = ["close"], at = [At("HEAD")], require = 1)
    private fun `teutonlib$freePlotLighting`(ci: CallbackInfo) {
        TrainPortalClientLighting.forget(this as VertexBuffer)
    }

    override fun `teutonlib$uploadLighting`(vertices: ByteBuffer) {
        RenderSystem.assertOnRenderThread()
        val previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBufferId)
        try {
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, vertices)
        } finally {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previous)
        }
    }
}
