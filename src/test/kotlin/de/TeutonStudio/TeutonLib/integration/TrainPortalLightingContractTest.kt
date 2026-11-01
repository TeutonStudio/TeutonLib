package de.TeutonStudio.TeutonLib.integration

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.util.jar.JarFile

class TrainPortalLightingContractTest {
    private fun sable(name: String): ClassNode {
        val file = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator)
            .map(::File).single { it.name == "sable-neoforge-1.21.1-2.0.6.jar" }
        return JarFile(file).use { jar ->
            ClassNode().also { node -> jar.getInputStream(jar.getJarEntry("dev/ryanhcode/sable/$name.class")).use {
                ClassReader(it).accept(node, 0)
            } }
        }
    }

    @Test fun `registered buffers exist before constructor can return to first compilation`() {
        val render = sable("sublevel/render/vanilla/VanillaChunkedSubLevelRenderData")
        val constructor = render.methods.single { it.name == "<init>" }
        val calls = constructor.instructions.filterIsInstance<MethodInsnNode>()
        assertEquals("resize", calls.last().name)
        val dispatcher = sable("sublevel/render/dispatcher/VanillaSubLevelRenderDispatcher")
        val create = dispatcher.methods.single { it.name == "createRenderData" }
        assertTrue(create.instructions.filterIsInstance<MethodInsnNode>().none { it.name == "compileSections" })
        val draw = render.methods.single { it.name == "renderChunkedSubLevel" }
        val drawCalls = draw.instructions.filterIsInstance<MethodInsnNode>()
        assertEquals(1, drawCalls.count { it.owner == "com/mojang/blaze3d/vertex/VertexBuffer" && it.name == "draw" })
        assertEquals(1, drawCalls.count { it.name == "getLatestSkyLightScale" })
    }

    @Test fun `Sodium dispatches Sable meshes through the same lighting hook`() {
        val sodium = sable("mixin/sublevel_render/impl/sodium/SodiumWorldRendererMixin")
        val draw = sodium.methods.single { it.name == "sable\$drawRenderSources" }
        assertTrue(draw.instructions.filterIsInstance<MethodInsnNode>().any {
            it.owner == "dev/ryanhcode/sable/sublevel/render/dispatcher/SubLevelRenderDispatcher" && it.name == "renderSectionLayer"
        })
    }

    @Test fun `native IP compile hook selects another world without restoring the shared dispatcher`() {
        val file = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator)
            .map(::File).single { it.name == "ip_aeronautics-2.6.4.jar" }
        val node = JarFile(file).use { jar -> ClassNode().also { target ->
            jar.getInputStream(jar.getJarEntry("qouteall/imm_ptl/core/compat/mixin_sable/MixinSableChunkedRenderData_DispatcherLevel.class")).use {
                ClassReader(it).accept(target, 0)
            }
        } }
        val hook = node.methods.single { it.name == "ip_compileFromOwnLevel" }
        assertEquals(1, hook.instructions.filterIsInstance<MethodInsnNode>().count {
            it.owner == "net/minecraft/client/renderer/chunk/SectionRenderDispatcher" && it.name == "setLevel"
        })
        assertTrue(hook.tryCatchBlocks.isEmpty())
        val inject = hook.visibleAnnotations.single { it.desc == "Lorg/spongepowered/asm/mixin/injection/Inject;" }
        val args = inject.values.chunked(2).associate { it[0] to it[1] }
        assertEquals(listOf("compileSections"), args["method"])
    }

    @Test fun `shared compile world is saved before the original and restored on both return paths`() {
        val node = ClassNode()
        javaClass.classLoader.getResourceAsStream("de/TeutonStudio/TeutonLib/mixin/train/client/TrainPortalChunkedLightingMixin.class")!!.use {
            ClassReader(it).accept(node, 0)
        }
        val wrapper = node.methods.single { it.name == "teutonlib\$scopeCompileWorld" }
        val calls = wrapper.instructions.filterIsInstance<MethodInsnNode>()
        val save = calls.indexOfFirst { it.name == "teutonlib\$getCompileLevel" }
        val original = calls.indexOfFirst { it.owner == "com/llamalad7/mixinextras/injector/wrapoperation/Operation" && it.name == "call" }
        val restores = calls.indices.filter { calls[it].name == "setLevel" }
        assertTrue(save >= 0 && original > save)
        assertEquals(2, restores.size)
        assertTrue(restores.all { it > original })
        assertTrue(wrapper.tryCatchBlocks.any { it.type == null })
        val annotation = wrapper.visibleAnnotations.single {
            it.desc == "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;"
        }
        val args = annotation.values.chunked(2).associate { it[0] to it[1] }
        val selector = (args["method"] as List<*>).single() as String
        assertTrue(sable("sublevel/render/vanilla/VanillaChunkedSubLevelRenderData").methods.any { it.name + it.desc == selector })
    }

    @Test fun `release applies mesh lighting only on client and carries its helpers`() {
        JarFile(System.getProperty("teutonlib.architectureArtifact")).use { jar ->
            val json = jar.getInputStream(jar.getJarEntry("teutonlib.mixins.json")).bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject
            }
            val common = json.getAsJsonArray("mixins").map { it.asString }.toSet()
            val client = json.getAsJsonArray("client").map { it.asString }.toSet()
            for (mixin in listOf("TrainPortalChunkedLightingMixin", "TrainPortalSectionDispatcherAccess", "TrainPortalVertexBufferLightingMixin", "TrainPortalBlockEntityLightingMixin",
                "TrainPortalFlywheelLightingMixin", "TrainPortalFlywheelEnvironmentLightingMixin", "TrainPortalFlywheelStorageWorldMixin")) {
                val name = "train.client.$mixin"
                assertTrue(name in client)
                assertFalse(name in common)
                assertNotNull(jar.getJarEntry("de/TeutonStudio/TeutonLib/mixin/train/client/$mixin.class"))
            }
            for (helper in listOf("TrainPortalClientLighting", "TrainPortalLightMath", "TrainPortalVertexBufferAccess", "TrainPortalFlywheelLighting")) {
                assertNotNull(jar.getJarEntry("de/TeutonStudio/TeutonLib/integration/train/client/$helper.class"))
            }
            val node = ClassNode()
            jar.getInputStream(jar.getJarEntry("de/TeutonStudio/TeutonLib/integration/train/client/TrainPortalClientLighting.class")).use {
                ClassReader(it).accept(node, 0)
            }
            assertTrue(node.methods.flatMap { it.instructions.filterIsInstance<MethodInsnNode>() }
                .none { it.name.contains("GetBufferSubData") }, "Rendering must never stall for GPU vertex readback")
        }
    }
}
