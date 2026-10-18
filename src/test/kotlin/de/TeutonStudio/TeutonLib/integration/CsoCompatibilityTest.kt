package de.TeutonStudio.TeutonLib.integration

import java.util.HashMap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CsoCompatibilityTest {
    @Test
    fun completeSupportedSetIsAvailable() {
        assertTrue(CsoCompatibility.problems(CsoCompatibilityTest.Companion.supported()).isEmpty())
    }

    @Test
    fun noOptionalModsReportsAllMissingDependencies() {
        assertEquals(3, CsoCompatibility.problems(java.util.Map.of<kotlin.String, kotlin.String>()).size)
    }

    @Test
    fun eachMissingDependencyDisablesTheWholeIntegration() {
        for (id in CsoCompatibilityTest.Companion.supported().keys) {
            val versions: kotlin.collections.MutableMap<kotlin.String, kotlin.String> =
                CsoCompatibilityTest.Companion.supported()
            versions.remove(id)
            val problems = CsoCompatibility.problems(versions)
            assertEquals(1, problems.size)
            assertTrue(problems.first().contains(id + " fehlt"))
        }
    }

    @Test
    fun untestedVersionsAreRejectedWithoutLinkingTheirClasses() {
        for (id in CsoCompatibilityTest.Companion.supported().keys) {
            val versions: kotlin.collections.MutableMap<kotlin.String, kotlin.String> =
                CsoCompatibilityTest.Companion.supported()
            versions.put(id, "999.0.0")
            assertEquals(1, CsoCompatibility.problems(versions).size)
            assertTrue(CsoCompatibility.problems(versions).first().contains("999.0.0"))
        }
    }

    companion object {
        @JvmStatic
        private fun supported(): kotlin.collections.MutableMap<kotlin.String, kotlin.String> {
            return HashMap<kotlin.String, kotlin.String>(
                java.util.Map.of<kotlin.String, kotlin.String>(
                    "create",
                    "6.0.10",
                    "sable",
                    "2.0.5",
                    "simulated",
                    "1.3.2"
                )
            )
        }
    }
}
