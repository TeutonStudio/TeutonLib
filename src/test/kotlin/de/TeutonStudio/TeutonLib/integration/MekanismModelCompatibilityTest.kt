package de.TeutonStudio.TeutonLib.integration

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MekanismModelCompatibilityTest {
    @Test
    fun `packet overrides require every verified optional release`() {
        val versions = mapOf("minecraft" to "1.21.1", "mekanism" to "10.7.19", "sable" to "2.0.6",
            "ip_aeronautics" to "2.6.4", "create_mekanism_compat" to "0.1.21")
        assertTrue(MekanismModelCompatibility.supports(versions))
        assertFalse(MekanismModelCompatibility.supports(emptyMap()))
        for (id in versions.keys) {
            assertFalse(MekanismModelCompatibility.supports(versions - id), "Absent $id")
            assertFalse(MekanismModelCompatibility.supports(versions + (id to "future")), "Changed $id")
        }
        assertTrue(MekanismModelCompatibility.supports(versions + ("unrelated" to "1.0")))
    }
}
