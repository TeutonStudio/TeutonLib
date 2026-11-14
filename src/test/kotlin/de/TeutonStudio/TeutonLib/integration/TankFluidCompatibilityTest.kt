package de.TeutonStudio.TeutonLib.integration

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarFile

class TankFluidCompatibilityTest {
    @Test fun `surface correction needs only the supported Create and Sable pair`() {
        val versions = mapOf("create" to "6.0.10", "sable" to "2.0.6")
        assertTrue(TankFluidCompatibility.supports(versions))
        for (id in versions.keys) {
            assertFalse(TankFluidCompatibility.supports(versions - id))
            assertFalse(TankFluidCompatibility.supports(versions + (id to "future")))
        }
        assertTrue(TankFluidCompatibility.supports(versions + ("another_mod" to "unrelated")))
    }

    @Test fun `verified native renderer has exactly one fluid level entry point`() {
        val classes = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator)
            .mapNotNull { file -> JarFile(file).use { jar -> jar.getJarEntry(TankFluidCompatibility.RENDERER)
                ?.let { jar.getInputStream(it).readBytes() } } }
        assertEquals(1, classes.size)
        TankFluidCompatibility.validate(classes.single())
        var calls = 0
        ClassReader(classes.single()).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (name != "renderSafe" || !descriptor.startsWith("(Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;")) return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                        if (owner == "com/simibubi/create/content/fluids/tank/FluidTankBlockEntity" && name == "getFluidLevel") calls++
                    }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        assertEquals(1, calls)
        assertThrows(IllegalStateException::class.java) { TankFluidCompatibility.validate(byteArrayOf(0)) }
    }

    @Test fun `surface hook ships on client only and no verification mod ships`() {
        JarFile(System.getProperty("teutonlib.architectureArtifact")).use { jar ->
            val config = jar.getInputStream(jar.getJarEntry("teutonlib.mixins.json")).bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject
            }
            val hook = "tank.client.FluidTankRendererMixin"
            assertTrue(config.getAsJsonArray("client").any { it.asString == hook })
            assertFalse(config.getAsJsonArray("mixins").any { it.asString == hook })
            assertNotNull(jar.getJarEntry("de/TeutonStudio/TeutonLib/mixin/tank/client/FluidTankRendererMixin.class"))
            assertFalse(jar.entries().asSequence().any { it.name.contains("tanktest/") })
        }
    }
}
