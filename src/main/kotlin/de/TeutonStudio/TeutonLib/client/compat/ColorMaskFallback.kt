package de.TeutonStudio.TeutonLib.client.compat

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import de.TeutonStudio.TeutonLib.compat.CompatibilityDiagnostics
import java.util.ArrayDeque
import net.createmod.catnip.gui.element.StencilElement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.neoforged.neoforge.client.GlStateBackup
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/** Opt-in emulation for Catnip's inverted stencil elements when no stencil FBO works. */
object ColorMaskFallback {
    private const val VERTEX_SHADER = """#version 150
out vec2 texCoord;
void main() {
    vec2 position = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    texCoord = position;
    gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
}
"""
    private const val FRAGMENT_SHADER = """#version 150
uniform sampler2D ContentSampler;
uniform sampler2D MaskSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 content = texture(ContentSampler, texCoord);
    float mask = step(0.001, texture(MaskSampler, texCoord).a);
    fragColor = vec4(content.rgb, content.a * (1.0 - mask));
}
"""

    private data class TargetPair(val mask: TextureTarget, val content: TextureTarget)

    private val available = ArrayDeque<TargetPair>()
    private var active = false
    private var program = 0
    private var vertexArray = 0

    @JvmStatic
    fun activate() {
        active = true
        CompatibilityDiagnostics.warnOnce(
            "ponder-color-mask-active",
            "TeutonLib: all stencil FBO strategies failed; using the experimental two-texture Catnip mask compositor"
        )
    }

    @JvmStatic
    fun isActive(): Boolean = active

    @JvmStatic
    fun render(element: StencilElement, graphics: GuiGraphics) {
        check(active) { "Color-mask fallback is not active" }
        RenderSystem.assertOnRenderThread()
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val width = window.width
        val height = window.height
        val originalFramebuffer = GlStateManager.getBoundFramebuffer()
        val backup = GlStateBackup()
        RenderSystem.backupGlState(backup)
        val pair = acquire(width, height)
        val pose = graphics.pose()
        var posePushed = false
        try {
            graphics.flush()
            pose.pushPose()
            posePushed = true
            element.transform(graphics)

            pair.mask.setClearColor(0f, 0f, 0f, 0f)
            pair.mask.clear(Minecraft.ON_OSX)
            pair.mask.bindWrite(true)
            element.renderStencil(graphics)
            graphics.flush()

            pair.content.setClearColor(0f, 0f, 0f, 0f)
            pair.content.clear(Minecraft.ON_OSX)
            pair.content.bindWrite(true)
            element.renderElement(graphics)
            graphics.flush()

            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, originalFramebuffer)
            GlStateManager._viewport(0, 0, width, height)
            composite(pair)
        } finally {
            if (posePushed) pose.popPose()
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, originalFramebuffer)
            GlStateManager._viewport(0, 0, width, height)
            RenderSystem.restoreGlState(backup)
            release(pair)
        }
    }

    @JvmStatic
    fun close() {
        available.forEach {
            it.mask.destroyBuffers()
            it.content.destroyBuffers()
        }
        available.clear()
        if (vertexArray != 0) {
            GL30.glDeleteVertexArrays(vertexArray)
            vertexArray = 0
        }
        if (program != 0) {
            GL20.glDeleteProgram(program)
            program = 0
        }
        active = false
    }

    private fun acquire(width: Int, height: Int): TargetPair {
        val pair = available.pollFirst() ?: TargetPair(
            TextureTarget(width, height, false, Minecraft.ON_OSX),
            TextureTarget(width, height, false, Minecraft.ON_OSX)
        )
        if (pair.mask.width != width || pair.mask.height != height) {
            pair.mask.resize(width, height, Minecraft.ON_OSX)
            pair.content.resize(width, height, Minecraft.ON_OSX)
        }
        return pair
    }

    private fun release(pair: TargetPair) {
        available.addLast(pair)
    }

    private fun composite(pair: TargetPair) {
        ensureProgram()
        GlStateManager._disableDepthTest()
        GlStateManager._enableBlend()
        GlStateManager._blendFuncSeparate(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE,
            GL11.GL_ONE_MINUS_SRC_ALPHA
        )
        GlStateManager._colorMask(true, true, true, true)

        GL20.glUseProgram(program)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, pair.content.colorTextureId)
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "ContentSampler"), 0)
        GL13.glActiveTexture(GL13.GL_TEXTURE1)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, pair.mask.colorTextureId)
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "MaskSampler"), 1)
        GL30.glBindVertexArray(vertexArray)
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3)
        GL30.glBindVertexArray(0)
        GL20.glUseProgram(0)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
    }

    private fun ensureProgram() {
        if (program != 0) return
        val vertex = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        try {
            program = GL20.glCreateProgram()
            GL20.glAttachShader(program, vertex)
            GL20.glAttachShader(program, fragment)
            GL20.glLinkProgram(program)
            check(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE) {
                "Color-mask shader link failed: ${GL20.glGetProgramInfoLog(program)}"
            }
            vertexArray = GL30.glGenVertexArrays()
        } finally {
            GL20.glDeleteShader(vertex)
            GL20.glDeleteShader(fragment)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GL20.glCreateShader(type)
        GL20.glShaderSource(shader, source)
        GL20.glCompileShader(shader)
        check(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE) {
            "Color-mask shader compile failed: ${GL20.glGetShaderInfoLog(shader)}"
        }
        return shader
    }
}
