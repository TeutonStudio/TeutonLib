package de.TeutonStudio.TeutonLib.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipInputStream

class LogisticsPackagingTest {
    private fun entries(bytes: ByteArray): Map<String, ByteArray> = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        buildMap {
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) put(entry.name, zip.readBytes())
            }
        }
    }

    @Test fun `release binaries match the recorded SHA256 lock`() {
        val dependencies = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator).map(::File)
        JarFile(System.getProperty("teutonlib.architectureArtifact")).use { release ->
            for (line in File("docs/logistics-compatibility.sha256").readLines()) {
                val (expected, name) = line.split("  ", limit = 2)
                val content = if (name.startsWith("mixinsquared-")) {
                    release.getInputStream(release.getJarEntry("META-INF/jarjar/$name")).readBytes()
                } else dependencies.single { it.name == name }.readBytes()
                val actual = MessageDigest.getInstance("SHA-256").digest(content)
                    .joinToString("") { "%02x".format(it) }
                assertEquals(expected, actual, name)
            }
        }
    }

    @Test fun `release includes both layers of MixinSquared and excludes foreign mods and test fixture`() {
        JarFile(System.getProperty("teutonlib.architectureArtifact")).use { jar ->
            val names = jar.entries().asSequence().map { it.name }.toList()
            assertFalse(names.any { it.contains("logisticstest/") || it.contains("fluidtest/") ||
                it.contains("create_factory_") || it.contains("fluidlogistics-") || it.contains("tfmg-1.2.0") })
            val embedded = names.single { it.startsWith("META-INF/jarjar/") && it.endsWith(".jar") }
            assertTrue(embedded.contains("mixinsquared-neoforge-0.3.7"))
            val neo = entries(jar.getInputStream(jar.getJarEntry(embedded)).readBytes())
            assertTrue("mixinsquared.neoforge.init.mixins.json" in neo)
            val common = entries(neo.getValue("META-INF/jars/MixinSquared-0.3.7.jar"))
            assertTrue("com/bawnorton/mixinsquared/api/MixinCanceller.class" in common)
            assertTrue("com/bawnorton/mixinsquared/MixinSquaredBootstrap.class" in common)
            val metadata = jar.getInputStream(jar.getJarEntry("META-INF/jarjar/metadata.json")).bufferedReader().readText()
            assertTrue(metadata.contains(embedded))
        }
    }

    @Test fun `canceller ignores every unrelated mixin without touching the activation guard`() {
        val canceller = LogisticsMixinCanceller()
        assertFalse(canceller.shouldCancel(emptyList(), "com.yision.fluidlogistics.mixin.OtherMixin"))
        assertFalse(canceller.shouldCancel(emptyList(), "ru.zznty.create_factory_logistics.mixin.logistics.stock.InventorySummaryMixin"))
        assertEquals(2, LogisticsCompatibility.replacements.size)
    }
}
