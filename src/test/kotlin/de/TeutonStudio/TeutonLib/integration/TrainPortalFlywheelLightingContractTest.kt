package de.TeutonStudio.TeutonLib.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.io.File
import java.util.jar.JarFile

class TrainPortalFlywheelLightingContractTest {
    private fun target(file: String, path: String): ClassNode {
        val jar = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator)
            .map(::File).single { it.name == file }
        return JarFile(jar).use { zip -> ClassNode().also { node ->
            zip.getInputStream(zip.getJarEntry(path)).use { ClassReader(it).accept(node, 0) }
        } }
    }

    @Test fun `both draw backends consume light only after environment matrices are flushed`() {
        val engine = target("flywheel-neoforge-1.21.1-1.0.6.jar", "dev/engine_room/flywheel/backend/engine/EngineImpl.class")
        val calls = engine.methods.single { it.name == "render" }.instructions.filterIsInstance<MethodInsnNode>()
        val flush = calls.indexOfFirst { it.owner.endsWith("/EnvironmentStorage") && it.name == "flush" }
        val draw = calls.indexOfFirst { it.owner.endsWith("/DrawManager") && it.name == "render" }
        assertTrue(flush >= 0 && draw > flush)
        assertEquals(1, calls.count { it.owner.endsWith("/DrawManager") && it.name == "render" })
    }

    @Test fun `Sable matrix layout exposes its sky multiplier at the pinned byte offset`() {
        val mixin = target("sable-neoforge-1.21.1-2.0.6.jar",
            "dev/ryanhcode/sable/neoforge/mixin/compatibility/flywheel/EmbeddedEnvironmentMixin.class")
        val flush = mixin.methods.single { it.name == "sable\$flush" }
        assertEquals(112L, flush.instructions.filterIsInstance<LdcInsnNode>().first().cst)
        assertTrue(flush.instructions.filterIsInstance<MethodInsnNode>().any {
            it.owner == "org/lwjgl/system/MemoryUtil" && it.name == "memPutFloat"
        })
        val storage = target("sable-neoforge-1.21.1-2.0.6.jar",
            "dev/ryanhcode/sable/neoforge/compatibility/flywheel/SableFlywheelLightStorage.class")
        assertTrue(storage.fields.any { it.name == "changed" && it.desc == "Ljava/util/BitSet;" })
        assertTrue(storage.fields.any { it.name == "scene2SectionArenaIndexMap" })
    }

    @Test fun `native light sections contain 18 cubed packed bytes in x z y order`() {
        val collector = target("flywheel-neoforge-1.21.1-1.0.6.jar", "dev/engine_room/flywheel/backend/engine/LightDataCollector.class")
        val write = collector.methods.single { it.name == "write" }
        val integerLoads = write.instructions.filterIsInstance<VarInsnNode>().filter { it.opcode == Opcodes.ILOAD }.map { it.`var` }
        assertEquals(listOf(2, 3, 4, 7, 9, 8, 5, 6, 10), integerLoads)
        val constants = write.instructions.filterIsInstance<IntInsnNode>().map { it.operand }
        assertEquals(3, constants.count { it == 18 })
        assertEquals(2, constants.count { it == 15 })
        assertTrue(write.instructions.filterIsInstance<MethodInsnNode>().any { it.name == "memPutByte" })
        val storage = target("flywheel-neoforge-1.21.1-1.0.6.jar", "dev/engine_room/flywheel/backend/engine/LightStorage.class")
        assertEquals(18 * 18 * 18, storage.fields.single { it.name == "LIGHT_SIZE_BYTES" }.value)
    }

    @Test fun `source world repair covers both native level reads and plot coordinate indexing`() {
        val storage = target("sable-neoforge-1.21.1-2.0.6.jar",
            "dev/ryanhcode/sable/neoforge/compatibility/flywheel/SableFlywheelLightStorage.class")
        for (name in listOf("updateLightSections", "removeUnusedSections")) {
            val method = storage.methods.single { it.name == name }
            assertEquals(1, method.instructions.filterIsInstance<org.objectweb.asm.tree.FieldInsnNode>().count {
                it.owner == "net/minecraft/client/Minecraft" && it.name == "level"
            })
        }
        val index = storage.methods.single { it.name == "indexForSection" }
        assertEquals(1, index.instructions.filterIsInstance<MethodInsnNode>().count {
            it.owner == "dev/ryanhcode/sable/ActiveSableCompanion" && it.name == "getContainingClient"
        })
    }

    @Test fun `scoped arena restoration survives exceptions from a draw backend`() {
        val node = ClassNode()
        javaClass.classLoader.getResourceAsStream(
            "de/TeutonStudio/TeutonLib/integration/train/client/TrainPortalFlywheelLighting.class")!!.use {
            ClassReader(it).accept(node, 0)
        }
        val pass = node.methods.single { it.name == "withPass" }
        assertTrue(pass.tryCatchBlocks.any { it.type == null }, "CPU light and matrix patches must have a finally path")
        assertTrue(pass.instructions.filterIsInstance<MethodInsnNode>().count {
            it.owner == "java/util/BitSet" && it.name == "set"
        } >= 2, "Restored CPU light must also invalidate the next GPU upload")
    }
}
