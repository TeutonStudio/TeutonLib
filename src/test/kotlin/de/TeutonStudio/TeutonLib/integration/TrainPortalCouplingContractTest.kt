package de.TeutonStudio.TeutonLib.integration

import java.io.File
import java.util.jar.JarFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode

class TrainPortalCouplingContractTest {
    private fun nativeClass(jarName: String, name: String): ClassNode {
        val jar = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator).map(::File)
            .single { it.name == jarName }
        return JarFile(jar).use { file ->
            ClassNode().also { ClassReader(file.getInputStream(file.getJarEntry(name)).readBytes()).accept(it, 0) }
        }
    }
    private fun createClass(name: String) = nativeClass("create-1.21.1-6.0.10.jar", name)

    @Test fun `close same-dimension gap uses the pinned native rail path instead of portal proximity`() {
        val path = "qouteall/imm_ptl/core/compat/create_integration/IPCreateTrainSides"
        assertTrue(TrainPortalCompatibility.contracts.any { it.mod == "ip_aeronautics" && it.path == "$path.class" },
            "the graph-path API must remain fingerprinted")
        val native = nativeClass("ip_aeronautics-2.6.4.jar", "$path.class")
        val gap = native.methods.single { it.name == "gapCrosses" }
        assertEquals("(Lcom/simibubi/create/content/trains/entity/Train;I)Z", gap.desc)
        assertEquals(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, gap.access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC))
        val calls = gap.instructions.filterIsInstance<MethodInsnNode>()
        assertTrue(calls.any { it.owner == path && it.name == "path" }, "must inspect the actual train graph")
        assertTrue(calls.any { it.owner.endsWith("/TrackEdge") && it.name == "isInterDimensional" })
        assertTrue(calls.any { it.owner.endsWith("/IPCreatePortalEdges") && it.name == "isLink" },
            "an ordinary track edge near a portal is insufficient")
        val helper = ClassNode().also { node -> javaClass.classLoader.getResourceAsStream(
            "de/TeutonStudio/TeutonLib/integration/train/client/TrainPortalCouplings.class")!!.use { ClassReader(it).accept(node, 0) } }
        val render = helper.methods.single { it.name == "render" }
        assertEquals(1, render.instructions.filterIsInstance<MethodInsnNode>().count {
            it.owner == path && it.name == "gapCrosses" && it.desc == gap.desc
        })
    }

    @Test fun `renderer replacement wraps exactly the two native dimensional lookups`() {
        val node = createClass("com/simibubi/create/content/trains/entity/CarriageCouplingRenderer.class")
        val render = node.methods.single { it.name == "renderAll" }
        val calls = render.instructions.filterIsInstance<MethodInsnNode>().filter { it.name == "getDimensional" }
        assertEquals(2, calls.size)
        assertTrue(calls.all { it.owner == "com/simibubi/create/content/trains/entity/Carriage" &&
            it.desc == "(Lnet/minecraft/world/level/Level;)Lcom/simibubi/create/content/trains/entity/Carriage\$DimensionalCarriageEntity;" })
        val mixin = ClassNode().also { n -> javaClass.classLoader.getResourceAsStream(
            "de/TeutonStudio/TeutonLib/mixin/train/client/CarriageCouplingPortalMixin.class")!!.use { ClassReader(it).accept(n, 0) } }
        val annotations = mixin.methods.flatMap { it.visibleAnnotations.orEmpty() }
        val lifecycle = annotations.filter { it.desc.endsWith("/Inject;") }.map { annotation ->
            val attributes = annotation.values.chunked(2).associate { it[0] to it[1] }
            assertEquals(listOf(render.name + render.desc), attributes["method"])
            assertEquals(1, attributes["require"])
            ((attributes["at"] as List<*>).single() as org.objectweb.asm.tree.AnnotationNode).values.chunked(2)
                .associate { it[0] to it[1] }["value"]
        }.toSet()
        assertEquals(setOf("HEAD", "RETURN"), lifecycle, "replacement ownership must last through the native draw loop")
        val wrap = annotations.single { it.desc.endsWith("/WrapOperation;") }
        val args = wrap.values.chunked(2).associate { it[0] to it[1] }
        assertEquals(2, args["require"])
        assertEquals(listOf(render.name + render.desc), args["method"])
    }

    @Test fun `sampling native anchors cannot change travelling points or train state`() {
        val node = createClass("com/simibubi/create/content/trains/entity/CarriageBogey.class")
        val update = node.methods.single { it.name == "updateCouplingAnchor" }
        val calls = update.instructions.filterIsInstance<MethodInsnNode>()
        assertEquals(1, calls.count { it.owner == "net/createmod/catnip/data/Couple" && it.name == "set" })
        assertFalse(calls.any { it.owner.endsWith("TravellingPoint") || it.owner.endsWith("Train") })
    }
}
