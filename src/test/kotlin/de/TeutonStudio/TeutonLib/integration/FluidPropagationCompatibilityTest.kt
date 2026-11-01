package de.TeutonStudio.TeutonLib.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile

class FluidPropagationCompatibilityTest {
    private val jars get() = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator).map(::File)
    private fun bytes(mod: String, path: String): ByteArray? {
        val name = when (mod) {
            "create" -> "create-1.21.1-6.0.10.jar"
            "fluidlogistics" -> "fluidlogistics-1.3.1-mc1.21.1.jar"
            "tfmg" -> "tfmg-1.2.0.jar"
            else -> error(mod)
        }
        return JarFile(jars.single { it.name == name }).use { zip ->
            zip.getJarEntry(path)?.let { zip.getInputStream(it).readBytes() }
        }
    }

    @Test fun `version selection requires the exact pair and does not require Factory Logistics`() {
        assertTrue(FluidPropagationCompatibility.supports(FluidPropagationCompatibility.supported))
        for (mod in FluidPropagationCompatibility.supported.keys) {
            assertFalse(FluidPropagationCompatibility.supports(FluidPropagationCompatibility.supported - mod), mod)
            assertFalse(FluidPropagationCompatibility.supports(FluidPropagationCompatibility.supported + (mod to "other")), mod)
        }
        assertTrue(FluidPropagationCompatibility.supports(FluidPropagationCompatibility.supported +
            mapOf("create_factory_logistics" to "1.4.9", "create_factory_abstractions" to "1.4.9")))
    }

    @Test fun `original classes match activation fingerprints and changed classes abort activation`() {
        FluidPropagationCompatibility.validate(::bytes)
        for (contract in FluidPropagationCompatibility.contracts) {
            for (replacement in listOf(null, byteArrayOf(0))) {
                assertThrows(IllegalStateException::class.java) {
                    FluidPropagationCompatibility.validate { mod, path ->
                        if (mod == contract.mod && path == contract.path) replacement else bytes(mod, path)
                    }
                }
            }
        }
    }

    @Test fun `release jars match recorded fingerprints`() {
        for (line in File("docs/fluid-propagation-compatibility.sha256").readLines()) {
            val (expected, name) = line.split("  ", limit = 2)
            val actual = MessageDigest.getInstance("SHA-256").digest(jars.single { it.name == name }.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, actual, name)
        }
    }

    @Test fun `replacement hooks use static exact targets and preserve sugar parameter annotations`() {
        val node = ClassNode()
        javaClass.classLoader.getResourceAsStream("de/TeutonStudio/TeutonLib/mixin/fluids/TfmgFluidPropagatorMixin.class")!!.use {
            ClassReader(it).accept(node, 0)
        }
        val mixin = node.invisibleAnnotations.single { it.desc == "Lorg/spongepowered/asm/mixin/Mixin;" }
        assertTrue(mixin.values.chunked(2).any { it[0] == "priority" && it[1] == 1100 })
        val handlers = node.methods.filter { method -> method.visibleAnnotations.orEmpty().any {
            it.desc.endsWith("/Inject;") || it.desc.endsWith("/ModifyExpressionValue;")
        } }
        assertEquals(4, handlers.size)
        val signature = "propagateChangedPipe(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"
        for (handler in handlers) {
            assertTrue(handler.access and org.objectweb.asm.Opcodes.ACC_STATIC != 0)
            val annotation = handler.visibleAnnotations.first { it.desc.endsWith("/Inject;") || it.desc.endsWith("/ModifyExpressionValue;") }
            val values = annotation.values.chunked(2).associate { it[0] to it[1] }
            assertEquals(listOf(signature), values["method"])
            assertEquals(1, values["require"])
            val parameters = handler.invisibleParameterAnnotations.orEmpty().filterNotNull().flatten()
            val expected = when {
                handler.name.contains("includeFluidPump") -> 1 to 0
                handler.name.contains("recordTraversal") -> 2 to 2
                handler.name.contains("captureFrontier") -> 5 to 1
                else -> 0 to 1
            }
            assertEquals(expected.first, parameters.count { it.desc == "Lcom/llamalad7/mixinextras/sugar/Local;" }, handler.name)
            assertEquals(expected.second, parameters.count { it.desc == "Lcom/llamalad7/mixinextras/sugar/Share;" }, handler.name)
        }
        val frontier = handlers.single { it.name.contains("captureFrontier") }
        val arguments = Type.getArgumentTypes(frontier.desc)
        val locals = frontier.invisibleParameterAnnotations
        val distance = arguments.indices.single { index -> arguments[index] == Type.INT_TYPE &&
            locals[index].orEmpty().any { it.desc == "Lcom/llamalad7/mixinextras/sugar/Local;" } }
        assertEquals(7, distance)
    }

    @Test fun `TFMG overwrite has primitive distance and retains both required invocation targets`() {
        val node = ClassNode()
        ClassReader(bytes("tfmg", "com/drmangotea/tfmg/mixin/FluidPropagatorMixin.class")!!).accept(node, 0)
        val method = node.methods.single { it.name == "propagateChangedPipe" }
        assertEquals("I", method.localVariables.single { it.name == "distance" }.desc)
        for (target in listOf("wipePressure()V", "hasAnyPressure()Z")) {
            assertEquals(1, method.instructions.toArray().filterIsInstance<MethodInsnNode>().count {
                it.owner == "com/simibubi/create/content/fluids/FluidTransportBehaviour" && it.name + it.desc == target
            })
        }
    }

    @Test fun `canceller leaves TFMG and unrelated FluidLogistics mixins intact`() {
        val canceller = FluidPropagationMixinCanceller()
        assertFalse(canceller.shouldCancel(emptyList(), "com.drmangotea.tfmg.mixin.FluidPropagatorMixin"))
        assertFalse(canceller.shouldCancel(emptyList(), "com.yision.fluidlogistics.mixin.logistics.RequestPromiseQueueMixin"))
    }
}
