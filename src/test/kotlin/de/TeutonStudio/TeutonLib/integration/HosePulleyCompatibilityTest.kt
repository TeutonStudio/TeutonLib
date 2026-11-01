package de.TeutonStudio.TeutonLib.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarFile

class HosePulleyCompatibilityTest {
    private val versions = mapOf("create" to "6.0.10", "sable" to "2.0.6")

    @Test
    fun `physical hose only activates for the verified optional pair`() {
        assertTrue(HosePulleyCompatibility.supports(versions))
        for (id in versions.keys) {
            assertFalse(HosePulleyCompatibility.supports(versions - id))
            assertFalse(HosePulleyCompatibility.supports(versions + (id to "future")))
        }
        assertFalse(HosePulleyCompatibility.supports(versions + ("hosepulley_sable_bridge" to "1.0.0")))
        assertTrue(HosePulleyCompatibility.supports(versions + ("computercraft" to "unrelated")))
    }

    @Test
    fun `Create private pulley contract matches the supported original binary`() {
        val members = mutableSetOf<String>()
        val path = "com/simibubi/create/content/fluids/hosePulley/HosePulleyBlockEntity.class"
        val classes = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator)
            .mapNotNull { file -> JarFile(file).use { jar -> jar.getJarEntry(path)?.let { jar.getInputStream(it).readBytes() } } }
        assertEquals(1, classes.size)
        ClassReader(classes.single()).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                members += name + descriptor
                return null
            }
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                members += name + descriptor
                return null
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        for (member in listOf(
            "offsetLnet/createmod/catnip/animation/LerpedFloat;", "isMovingZ",
            "tick()V", "lazyTick()V", "getMovementSpeed()F", "onSpeedChanged(F)V",
            "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup\$Provider;Z)V",
            "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup\$Provider;Z)V"
        )) assertTrue(member in members, "Missing Create pulley contract: $member")
    }
}
