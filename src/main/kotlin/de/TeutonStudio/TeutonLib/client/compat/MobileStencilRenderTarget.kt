package de.TeutonStudio.TeutonLib.client.compat

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.platform.TextureUtil
import com.mojang.blaze3d.platform.Window
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import de.TeutonStudio.TeutonLib.compat.CompatibilityDiagnostics
import de.TeutonStudio.TeutonLib.compat.MobileEnvironment
import net.createmod.catnip.gui.UIRenderHelper.CustomRenderTarget
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.neoforged.neoforge.client.GlStateBackup
import net.neoforged.neoforge.common.NeoForgeConfig
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil

/** A Catnip target whose depth/stencil allocation does not depend on RenderTarget.enableStencil(). */
class MobileStencilRenderTarget private constructor(private var strategy: Strategy) : CustomRenderTarget(true) {
    private var renderBufferId = -1
    private var recoverResizeFailures = false

    override fun resize(width: Int, height: Int, clearError: Boolean) {
        if (!recoverResizeFailures) {
            super.resize(width, height, clearError)
            return
        }
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall { resize(width, height, clearError) }
            return
        }

        val original = strategy
        val failures = mutableListOf<String>()
        for (candidate in listOf(original) + Strategy.entries.filterNot { it == original }) {
            strategy = candidate
            try {
                super.resize(width, height, clearError)
                check(verifyStencilMask()) { "functional stencil mask test failed after resize" }
                clear(clearError)
                CompatibilityDiagnostics.infoOnce(
                    "ponder-resize-${candidate.name}",
                    "TeutonLib: Ponder framebuffer resize selected {} at {}x{}",
                    candidate.displayName,
                    width,
                    height
                )
                return
            } catch (failure: Exception) {
                failures += "${candidate.displayName}: ${failure.message ?: failure.javaClass.simpleName}"
                runCatching { destroyBuffers() }
            }
        }
        strategy = original
        throw IllegalStateException(
            "TeutonLib could not resize the Ponder framebuffer to ${width}x$height: ${failures.joinToString("; ")}"
        )
    }

    override fun createBuffers(width: Int, height: Int, clearError: Boolean) {
        RenderSystem.assertOnRenderThreadOrInit()
        val max = RenderSystem.maxSupportedTextureSize()
        require(width > 0 && width <= max && height > 0 && height <= max) {
            "Window ${width}x$height size out of bounds (max. size: $max)"
        }

        viewWidth = width
        viewHeight = height
        this.width = width
        this.height = height
        frameBufferId = GlStateManager.glGenFramebuffers()
        colorTextureId = TextureUtil.generateTextureId()

        GlStateManager._bindTexture(colorTextureId)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GlStateManager._texImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA8,
            width,
            height,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            null
        )
        filterMode = GL11.GL_NEAREST

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBufferId)
        GlStateManager._glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D,
            colorTextureId,
            0
        )

        if (strategy.renderBuffer) createRenderBuffer(width, height) else createDepthStencilTexture(width, height)

        checkStatus()
        clear(clearError)
        unbindRead()
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
    }

    private fun createDepthStencilTexture(width: Int, height: Int) {
        depthBufferId = TextureUtil.generateTextureId()
        GlStateManager._bindTexture(depthBufferId)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager._texImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            strategy.internalFormat,
            width,
            height,
            0,
            GL30.GL_DEPTH_STENCIL,
            strategy.pixelType,
            null
        )
        if (strategy.combinedAttachment()) {
            GlStateManager._glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL11.GL_TEXTURE_2D,
                depthBufferId,
                0
            )
        } else {
            GlStateManager._glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D,
                depthBufferId,
                0
            )
            GlStateManager._glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT,
                GL11.GL_TEXTURE_2D,
                depthBufferId,
                0
            )
        }
    }

    private fun createRenderBuffer(width: Int, height: Int) {
        renderBufferId = GlStateManager.glGenRenderbuffers()
        GlStateManager._glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderBufferId)
        GlStateManager._glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height)
        GlStateManager._glFramebufferRenderbuffer(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_DEPTH_STENCIL_ATTACHMENT,
            GL30.GL_RENDERBUFFER,
            renderBufferId
        )
        GlStateManager._glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0)
    }

    override fun destroyBuffers() {
        RenderSystem.assertOnRenderThreadOrInit()
        unbindRead()
        unbindWrite()
        if (depthBufferId > -1) {
            TextureUtil.releaseTextureId(depthBufferId)
            depthBufferId = -1
        }
        if (renderBufferId > -1) {
            GlStateManager._glDeleteRenderbuffers(renderBufferId)
            renderBufferId = -1
        }
        if (colorTextureId > -1) {
            TextureUtil.releaseTextureId(colorTextureId)
            colorTextureId = -1
        }
        if (frameBufferId > -1) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
            GlStateManager._glDeleteFramebuffers(frameBufferId)
            frameBufferId = -1
        }
    }

    override fun clear(clearError: Boolean) {
        super.clear(clearError)
        bindWrite(false)
        GlStateManager._stencilMask(0xff)
        GlStateManager._clearStencil(0)
        GlStateManager._clear(GL11.GL_STENCIL_BUFFER_BIT, clearError)
        unbindWrite()
    }

    override fun enableStencil() = Unit

    override fun isStencilEnabled(): Boolean = true

    private fun verifyStencilMask(): Boolean {
        val backup = GlStateBackup()
        RenderSystem.backupGlState(backup)
        val previousProjection = Matrix4f(RenderSystem.getProjectionMatrix())
        val previousShader = RenderSystem.getShader()
        val modelView = RenderSystem.getModelViewStack()
        val pixels = MemoryUtil.memAlloc(8)
        var modelViewPushed = false
        return try {
            bindWrite(true)
            GL11.glDisable(GL11.GL_STENCIL_TEST)
            GlStateManager._disableDepthTest()
            GlStateManager._disableBlend()
            GlStateManager._disableScissorTest()
            GlStateManager._colorMask(true, true, true, true)
            GlStateManager._stencilMask(0xff)
            GlStateManager._clearColor(0f, 0f, 0f, 0f)
            GlStateManager._clearStencil(0)
            GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_STENCIL_BUFFER_BIT, false)

            GlStateManager._enableScissorTest()
            GlStateManager._scissorBox(0, 0, 4, 8)
            GlStateManager._clearStencil(1)
            GlStateManager._clear(GL11.GL_STENCIL_BUFFER_BIT, false)
            GlStateManager._disableScissorTest()

            GL11.glEnable(GL11.GL_STENCIL_TEST)
            GlStateManager._stencilMask(0)
            GlStateManager._stencilFunc(GL11.GL_EQUAL, 1, 0xff)
            GlStateManager._stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)

            modelView.pushMatrix()
            modelViewPushed = true
            modelView.identity()
            RenderSystem.applyModelViewMatrix()
            RenderSystem.setProjectionMatrix(Matrix4f().setOrtho(0f, 8f, 0f, 8f, -1f, 1f), com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z)
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
            val builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            builder.addVertex(0f, 0f, 0f).setColor(255, 0, 0, 255)
            builder.addVertex(8f, 0f, 0f).setColor(255, 0, 0, 255)
            builder.addVertex(8f, 8f, 0f).setColor(255, 0, 0, 255)
            builder.addVertex(0f, 8f, 0f).setColor(255, 0, 0, 255)
            BufferUploader.drawWithShader(builder.buildOrThrow())
            modelView.popMatrix()
            modelViewPushed = false
            RenderSystem.applyModelViewMatrix()

            GlStateManager._readPixels(1, 4, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
            GlStateManager._readPixels(6, 4, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels.position(4))
            val insideRed = pixels.get(0).toInt() and 0xff
            val outsideRed = pixels.get(4).toInt() and 0xff
            insideRed > 200 && outsideRed < 20
        } finally {
            pixels.position(0)
            MemoryUtil.memFree(pixels)
            if (modelViewPushed) {
                modelView.popMatrix()
                RenderSystem.applyModelViewMatrix()
            }
            GL11.glDisable(GL11.GL_STENCIL_TEST)
            GlStateManager._stencilMask(0xff)
            GlStateManager._colorMask(true, true, true, true)
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
            RenderSystem.setProjectionMatrix(previousProjection, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z)
            if (previousShader != null) RenderSystem.setShader { previousShader }
            RenderSystem.restoreGlState(backup)
        }
    }

    private enum class Strategy(
        val displayName: String,
        val internalFormat: Int,
        val pixelType: Int,
        val attachment: Attachment,
        val renderBuffer: Boolean = false
    ) {
        ORIGINAL(
            "NeoForge D32FS8 texture",
            GL30.GL_DEPTH32F_STENCIL8,
            GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV,
            Attachment.CONFIGURED
        ),
        D24_SEPARATE(
            "D24S8 texture with separate attachments",
            GL30.GL_DEPTH24_STENCIL8,
            GL30.GL_UNSIGNED_INT_24_8,
            Attachment.SEPARATE
        ),
        D24_COMBINED(
            "D24S8 texture with combined attachment",
            GL30.GL_DEPTH24_STENCIL8,
            GL30.GL_UNSIGNED_INT_24_8,
            Attachment.COMBINED
        ),
        D24_RENDERBUFFER(
            "D24S8 renderbuffer",
            GL30.GL_DEPTH24_STENCIL8,
            GL30.GL_UNSIGNED_INT_24_8,
            Attachment.COMBINED,
            true
        );

        fun combinedAttachment(): Boolean = when (attachment) {
            Attachment.COMBINED -> true
            Attachment.SEPARATE -> false
            Attachment.CONFIGURED -> NeoForgeConfig.CLIENT.useCombinedDepthStencilAttachment.get()
        }
    }

    private enum class Attachment { CONFIGURED, SEPARATE, COMBINED }

    companion object {
        @JvmStatic
        fun createMobile(window: Window): CustomRenderTarget {
            RenderSystem.assertOnRenderThreadOrInit()
            val failures = mutableListOf<String>()
            for (strategy in Strategy.entries) {
                var probe: MobileStencilRenderTarget? = null
                var target: MobileStencilRenderTarget? = null
                try {
                    probe = MobileStencilRenderTarget(strategy)
                    probe.resize(8, 8, Minecraft.ON_OSX)
                    check(probe.verifyStencilMask()) { "functional stencil mask test failed" }
                    probe.destroyBuffers()
                    probe = null

                    target = MobileStencilRenderTarget(strategy)
                    target.resize(window.width, window.height, Minecraft.ON_OSX)
                    check(target.verifyStencilMask()) { "functional stencil mask test failed at window size" }
                    target.setClearColor(0f, 0f, 0f, 0f)
                    target.clear(Minecraft.ON_OSX)
                    target.recoverResizeFailures = true
                    CompatibilityDiagnostics.infoOnce(
                        "ponder-framebuffer-${strategy.name}",
                        "TeutonLib: Ponder framebuffer uses {} at {}x{}",
                        strategy.displayName,
                        window.width,
                        window.height
                    )
                    return target
                } catch (failure: Exception) {
                    failures += "${strategy.displayName}: ${failure.message ?: failure.javaClass.simpleName}"
                    runCatching { probe?.destroyBuffers() }
                    runCatching { target?.destroyBuffers() }
                }
            }

            if (MobileEnvironment.isColorMaskEnabled()) {
                val fallback = CustomRenderTarget(false)
                fallback.resize(window.width, window.height, Minecraft.ON_OSX)
                fallback.setClearColor(0f, 0f, 0f, 0f)
                ColorMaskFallback.activate()
                return fallback
            }
            throw IllegalStateException(
                "TeutonLib could not create a functional Ponder depth/stencil target: ${failures.joinToString("; ")}. " +
                    "The experimental fallback can be enabled with -D${MobileEnvironment.COLOR_MASK_PROPERTY}=true"
            )
        }
    }
}
