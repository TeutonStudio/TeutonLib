package de.TeutonStudio.TeutonLib.integration

import java.io.File
import java.util.jar.JarFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

class HybridTrainCompatibilityTest {
    private fun bytes(mod: String, path: String): ByteArray? {
        val filename = when (mod) {
            "create" -> "create-1.21.1-6.0.10.jar"
            "sable" -> "sable-neoforge-1.21.1-2.0.6.jar"
            else -> "create_train_functionality-0.1.47.jar"
        }
        val file = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator).map(::File).single { it.name == filename }
        return JarFile(file).use { jar -> jar.getJarEntry(path)?.let { jar.getInputStream(it).readBytes() } }
    }

    @Test fun `hybrid simulation works without a portal or radar dependency`() {
        assertTrue(HybridTrainCompatibility.supports(HybridTrainCompatibility.supported))
        assertFalse(HybridTrainCompatibility.supports(emptyMap()))
        for (id in HybridTrainCompatibility.supported.keys) {
            assertFalse(HybridTrainCompatibility.supports(HybridTrainCompatibility.supported - id))
            assertFalse(HybridTrainCompatibility.supports(HybridTrainCompatibility.supported + (id to "unverified")))
        }
        assertFalse("ip_aeronautics" in HybridTrainCompatibility.supported)
        assertFalse("create_radar" in HybridTrainCompatibility.supported)
        HybridTrainCompatibility.validate(::bytes)
        HybridTrainCompatibility.contracts.forEach { (changed, _) ->
            assertThrows(IllegalStateException::class.java) {
                HybridTrainCompatibility.validate { mod, path -> if (path == changed) byteArrayOf(0) else bytes(mod, path) }
            }
        }
    }

    @Test fun `all native CTF lifecycle selectors target pinned original methods`() {
        for ((mixin, target) in listOf("HybridDefaultMixin" to "AutoConvert", "HybridConversionMixin" to "SubLevelLinks",
            "HybridCleanupMixin" to "SubLevelLinks")) {
            val native = ClassNode().also { ClassReader(bytes("create_train_functionality", "createtrainfunctionality/$target.class")!!).accept(it, 0) }
            val compiled = ClassNode().also { node -> javaClass.classLoader.getResourceAsStream(
                "de/TeutonStudio/TeutonLib/mixin/hybrid/$mixin.class")!!.use { ClassReader(it).accept(node, 0) } }
            val selectors = compiled.methods.flatMap { it.visibleAnnotations.orEmpty() }
                .filter { it.desc.endsWith("/WrapMethod;") || it.desc.endsWith("/Inject;") || it.desc.endsWith("/ModifyExpressionValue;") }
                .flatMap { annotation -> (annotation.values.chunked(2).associate { it[0] to it[1] }.getValue("method") as List<*>).map { it as String } }
            assertTrue(selectors.isNotEmpty(), mixin)
            selectors.forEach { selector -> assertEquals(1, native.methods.count { it.name + it.desc == selector }, "$mixin: $selector") }
        }
    }

    @Test fun `release ships the hybrid implementation without optional mod copies or fixtures`() {
        JarFile(System.getProperty("teutonlib.architectureArtifact")).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toSet()
            assertTrue("de/TeutonStudio/TeutonLib/integration/hybrid/HybridCarriageContraption.class" in entries)
            assertTrue("de/TeutonStudio/TeutonLib/integration/hybrid/HybridTrainRuntime.class" in entries)
            assertFalse(entries.any { it.startsWith("createtrainfunctionality/") || it.startsWith("com/happysg/radar/") ||
                it.startsWith("de/TeutonStudio/TeutonLib/stationtest/") ||
                it.contains("HybridTrainVerification") })
        }
    }
}
