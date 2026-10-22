package de.TeutonStudio.TeutonLib

import de.TeutonStudio.TeutonLib.mixin.peripheral.simulated.GimbalSensorPeripheralMixin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class GimbalPeripheralMixinCompatibilityTest {
    @Test
    fun luaMethodsRemainOverridableByCompatibilityPeripherals() {
        val methods = HashMap<String, Int>()
        val resource = "/${GimbalSensorPeripheralMixin::class.java.name.replace('.', '/')}.class"
        requireNotNull(javaClass.getResourceAsStream(resource)).use { stream ->
            ClassReader(stream).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor? {
                    methods[name + descriptor] = access
                    return null
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }

        assertFalse(methods.keys.any { it.startsWith("getAngles(") }, "Simulated getAngles must not be overwritten")
        assertFalse(methods.keys.any { it.startsWith("getAnglesRad(") }, "Simulated getAnglesRad must not be overwritten")

        val signatures = listOf(
            "getYaw()D",
            "getPitch()D",
            "getRoll()D",
            "getRotation()Ljava/util/List;",
            "getRotaion()Ljava/util/List;"
        )
        signatures.forEach { signature ->
            assertTrue(methods.containsKey(signature), "Missing gimbal method: $signature")
            assertEquals(Opcodes.ACC_PUBLIC, methods.getValue(signature) and Opcodes.ACC_PUBLIC, signature)
            assertNotEquals(Opcodes.ACC_FINAL, methods.getValue(signature) and Opcodes.ACC_FINAL, "$signature must remain overridable")
        }
    }
}
