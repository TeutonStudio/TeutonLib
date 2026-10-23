package de.TeutonStudio.TeutonLib

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.jar.JarFile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Prevents CC-Aeroworks imports, descriptors, Mixins, reflection, desk ids and metadata links. */
class ArchitectureBoundaryTest {
    @Test
    fun `built jar has no Aeroworks dependency`() {
        val artifact = Path.of(requireNotNull(System.getProperty("teutonlib.architectureArtifact")) {
            "Gradle must provide the TeutonLib architecture artifact"
        })
        val violations = JarFile(artifact.toFile()).use { jar ->
            jar.entries().asSequence()
                .filterNot { it.isDirectory }
                .mapNotNull { entry ->
                    val content = jar.getInputStream(entry).use { it.readBytes() }
                        .toString(StandardCharsets.ISO_8859_1)
                    entry.name.takeIf { marker in content }
                }
                .toSortedSet()
        }

        assertTrue(violations.isEmpty(), "TeutonLib must not depend on CC-Aeroworks: $violations")
    }

    private companion object {
        val marker = Regex(
            "de[/\\.]teutonstudio[/\\.]ccaeroworks|cc_aeroworks:desk_id|modId=\\\"cc_aeroworks\\\""
        )
    }
}
