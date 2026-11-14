package de.TeutonStudio.TeutonLib

import java.util.jar.JarFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MobileExtractionPackagingTest {
    @Test
    fun libraryNoLongerShipsModpackMobilePatches() {
        JarFile(System.getProperty("teutonlib.architectureArtifact")).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()
            assertTrue(entries.none {
                it.contains("/mixin/mobile/") || it.contains("/client/compat/") ||
                    it.endsWith("/MobileEnvironment.class") || it.contains("EssentialSocketAdapter") ||
                    it.startsWith("de/TeutonStudio/mobilecompat/") || it.startsWith("gg/essential/")
            })
            val config = jar.getInputStream(jar.getJarEntry("teutonlib.mixins.json")).bufferedReader().use { it.readText() }
            assertFalse(config.contains("mobile."))
        }
    }
}
