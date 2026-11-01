package de.TeutonStudio.TeutonLib.integration

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.jar.JarFile

class NewAgeMotorCompatibilityTest {
    private val motor = "org/antarcticgardens/cna/content/motor/MotorBlockEntity"
    private val mixin = "de/TeutonStudio/TeutonLib/mixin/newage/NewAgeMotorRotationMixin"

    @Test
    fun `motor correction requires every verified release and permits unrelated mods`() {
        val versions = mapOf("minecraft" to "1.21.1", "create" to "6.0.10", "sable" to "2.0.6",
            "create_new_age" to "1.1.7c+mc1.21.1")
        assertTrue(NewAgeMotorCompatibility.supports(versions))
        assertFalse(NewAgeMotorCompatibility.supports(emptyMap()))
        for (id in versions.keys) {
            assertFalse(NewAgeMotorCompatibility.supports(versions - id), "Absent $id")
            assertFalse(NewAgeMotorCompatibility.supports(versions + (id to "future")), "Changed $id")
        }
        assertTrue(NewAgeMotorCompatibility.supports(versions + ("computercraft" to "unrelated")))
    }

    @Test
    fun `original New Age motor retains both private speed reads and the reconciliation tick`() {
        JarFile(System.getProperty("teutonlib.newAgeJar")).use { jar ->
            val original = node(jar.getInputStream(jar.getJarEntry("$motor.class")).readBytes())
            val speed = original.fields.single { it.name == "speed" && it.desc == "F" }
            assertTrue(speed.access and Opcodes.ACC_PRIVATE != 0)
            val rotation = original.methods.single { it.name == "updateGeneratedRotation" && it.desc == "()V" }
            assertEquals(2, rotation.instructions.toArray().filterIsInstance<FieldInsnNode>().count {
                it.opcode == Opcodes.GETFIELD && it.owner == motor && it.name == "speed" && it.desc == "F"
            }, "The mixin must replace both comparisons against New Age's separate speed cache")
            assertTrue(original.methods.any { it.name == "tick" && it.desc == "()V" })
        }
    }

    @Test
    fun `release registers an optional exact motor hook and connects its version guard`() {
        JarFile(System.getProperty("teutonlib.architectureArtifact")).use { jar ->
            fun releaseNode(path: String) = node(jar.getInputStream(jar.getJarEntry("$path.class")).readBytes())
            val replacement = releaseNode(mixin)
            val classAnnotations = replacement.visibleAnnotations.orEmpty() + replacement.invisibleAnnotations.orEmpty()
            assertTrue(classAnnotations.any { it.desc == "Lorg/spongepowered/asm/mixin/Pseudo;" })
            val target = classAnnotations.single { it.desc == "Lorg/spongepowered/asm/mixin/Mixin;" }.properties()
            assertEquals(listOf(motor.replace('/', '.')), target["targets"])
            assertEquals(false, target["remap"])

            val rotation = replacement.methods.single { method -> method.visibleAnnotations.orEmpty().any {
                it.desc == "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;"
            } }
            val rotationHook = rotation.visibleAnnotations.single {
                it.desc == "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;"
            }.properties()
            assertEquals(listOf("updateGeneratedRotation()V"), rotationHook["method"])
            assertEquals(2, rotationHook["require"])
            assertEquals(2, rotationHook["expect"])
            val field = (rotationHook["at"] as List<*>).single() as AnnotationNode
            assertEquals("FIELD", field.properties()["value"])
            assertEquals("L$motor;speed:F", field.properties()["target"])
            assertEquals(Opcodes.GETFIELD, field.properties()["opcode"])

            val tickHook = replacement.methods.flatMap { it.visibleAnnotations.orEmpty() }
                .single { it.desc == "Lorg/spongepowered/asm/mixin/injection/Inject;" }.properties()
            assertEquals(listOf("tick()V"), tickHook["method"])
            assertEquals("TAIL", ((tickHook["at"] as List<*>).single() as AnnotationNode).properties()["value"])

            val config = JsonParser.parseString(jar.getInputStream(jar.getJarEntry("teutonlib.mixins.json"))
                .bufferedReader().use { it.readText() }).asJsonObject
            assertEquals(1, config.getAsJsonArray("mixins").count { it.asString == "newage.NewAgeMotorRotationMixin" })
            assertFalse(config.getAsJsonArray("client").any { it.asString == "newage.NewAgeMotorRotationMixin" })
            val plugin = releaseNode("de/TeutonStudio/TeutonLib/integration/TeutonLibMixinPlugin")
                .methods.single { it.name == "shouldApplyMixin" }
            val instructions = plugin.instructions.toArray()
            assertTrue(instructions.filterIsInstance<LdcInsnNode>().any { it.cst == ".mixin.newage." })
            assertTrue(instructions.filterIsInstance<MethodInsnNode>().any {
                it.owner == "de/TeutonStudio/TeutonLib/integration/NewAgeMotorCompatibility" &&
                    it.name == "active" && it.desc == "()Z"
            })
            assertFalse(jar.entries().asSequence().any {
                it.name.startsWith("org/antarcticgardens/cna/") || it.name.contains("/energytest/")
            }, "Optional dependency classes and development fixtures must stay out of the release")
        }
    }

    private fun node(bytes: ByteArray) = ClassNode().also { ClassReader(bytes).accept(it, 0) }

    private fun AnnotationNode.properties(): Map<String, Any?> =
        values.orEmpty().chunked(2).associate { it[0] as String to it[1] }
}
