package de.TeutonStudio.TeutonLib

import de.TeutonStudio.TeutonLib.integration.peripheral.simulated.DirectionalGearshiftPeripheral
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class DirectionalGearshiftPeripheralContractTest {
    @Test
    fun exposesLinkedRedstoneAndCompatibilityMethodsAsLuaFunctions() {
        val luaMethods = mutableMapOf<String, Boolean>()
        val resource = "/${DirectionalGearshiftPeripheral::class.java.name.replace('.', '/')}.class"
        requireNotNull(javaClass.getResourceAsStream(resource)).use { stream ->
            ClassReader(stream).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                        if (descriptor != "Ldan200/computercraft/api/lua/LuaFunction;") return null
                        luaMethods[name] = false
                        return object : AnnotationVisitor(Opcodes.ASM9) {
                            override fun visit(annotationName: String, value: Any) {
                                if (annotationName == "mainThread") luaMethods[name] = value as Boolean
                            }
                        }
                    }
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }

        val mutating = setOf("setOutput", "setAnalogOutput", "setLeft", "setRight", "setOutputs", "clear")
        val expected = mutating + setOf(
            "getOutput", "getAnalogOutput", "isLeftPowered", "isRightPowered",
            "getRotationModifier", "getStatus", "getFacing", "getPosition", "getClassName", "help"
        )
        assertTrue(luaMethods.keys.containsAll(expected), "Missing Lua methods: ${expected - luaMethods.keys}")
        mutating.forEach { assertEquals(true, luaMethods[it], "$it must execute on the server thread") }
    }
}
