package de.TeutonStudio.TeutonLib.integration.cso.docking

import java.io.File
import java.util.jar.JarFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode

/** Protect the precise 1.3.2 hooks and the geometry paths shared by rendering and physics. */
class DockingBinaryContractTest {
    private val prefix = "dev/simulated_team/simulated/content/blocks/docking_connector/"
    private fun read(name: String): ClassNode {
        val matches = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator)
            .mapNotNull { file -> JarFile(file).use { jar ->
                jar.getJarEntry("$prefix$name.class")?.let { jar.getInputStream(it).use { stream -> stream.readBytes() } }
            } }
        assertEquals(1, matches.size, "unique pinned binary for $name")
        return ClassNode().also { ClassReader(matches.single()).accept(it, 0) }
    }
    private fun calls(type: String, method: String, owner: String, target: String): Boolean =
        read(type).methods.filter { it.name == method }.flatMap { it.instructions.toArray().toList() }
            .filterIsInstance<MethodInsnNode>().any { it.owner == owner && it.name == target }

    @Test fun `tick and initialize allow preserving SmartBlockEntity lifecycle`() {
        for (method in listOf("tick", "initialize")) assertTrue(calls("DockingConnectorBlockEntity", method,
            "com/simibubi/create/foundation/blockEntity/SmartBlockEntity", method))
        val methods = read("DockingConnectorBlockEntity").methods.map { it.name + it.desc }
        for (signature in listOf("updateState()V", "unDock()V", "updateSignal()V", "remove()V", "isExtended()Z",
            "getTipPosition()Lnet/minecraft/world/phys/Vec3;", "getMagnetPosition()Lnet/minecraft/world/phys/Vec3;")) assertTrue(signature in methods, signature)
        assertTrue(read("DockingConnectorPair").methods.any { it.name == "dock" && it.desc == "(Z)V" })
        assertTrue(read("DockingConnectorPair").methods.any { it.name == "canConnect" && it.desc == "(Lorg/joml/Vector3d;Lorg/joml/Vector3d;Lorg/joml/Vector3d;)Z" })
    }

    @Test fun `renderer collision and both constraint builders consume connector geometry`() {
        val dock = prefix + "DockingConnectorBlockEntity"
        assertTrue(calls("DockingConnectorRenderer", "renderSafe", dock, "getExtensionDistance"))
        assertTrue(calls("DockingConnectorBlockEntity", "getBoundingBox", dock, "getExtensionDistance"))
        assertTrue(calls("DockingConnectorBlockEntity", "attachConstraints", dock, "getTipPosition"))
        assertTrue(calls("DockingConnectorBlockEntity\$ConstraintSmoother", "step", dock, "getTipPosition"))
        assertTrue(calls("DockingConnectorPair", "getRelativeTipPosition", dock, "getTipPosition"))
    }

    @Test fun `upstream unlock still disconnects all resource bridges`() {
        for ((owner, method) in listOf(
            prefix + "DockingConnectorTank" to "disconnect",
            prefix + "DockingConnectorBattery" to "disconnect",
            "dev/simulated_team/simulated/compat/computercraft/wired/DockingConnectorWiredElement" to "disconnect",
            prefix + "DockingConnectorBlockEntity" to "removeConstraint")) {
            assertTrue(calls("DockingConnectorBlockEntity", "unDock", owner, method), "$owner.$method")
        }
    }
}
