package de.TeutonStudio.TeutonLib.integration.peripheral

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeripheralLinkContractTest {
    @Test
    fun nicknameValidationMatchesComputerEditorContract() {
        assertEquals("Cargo sensor", PeripheralLinkBlockEntity.validateNickname("  Cargo sensor  "))
        assertEquals("", PeripheralLinkBlockEntity.validateNickname("   "))
        assertThrows(IllegalArgumentException::class.java) {
            PeripheralLinkBlockEntity.validateNickname("x".repeat(33))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PeripheralLinkBlockEntity.validateNickname("invalid\nname")
        }
    }

    @Test
    fun canonicalResourcesUsePeripheralLinkIdOnly() {
        val resources = Path.of("src/main/resources")
        val canonical = listOf(
            "assets/teutonlib/blockstates/peripheral_link.json",
            "assets/teutonlib/models/block/peripheral_link.json",
            "assets/teutonlib/models/item/peripheral_link.json",
            "data/teutonlib/loot_table/blocks/peripheral_link.json",
            "data/teutonlib/recipe/peripheral_link.json",
            "data/teutonlib/recipe/peripheral_link_from_components.json"
        )
        canonical.forEach { assertTrue(Files.isRegularFile(resources.resolve(it)), "Missing $it") }
        assertFalse(Files.exists(resources.resolve("assets/teutonlib/blockstates/peripheral_modem.json")))
        assertFalse(Files.exists(resources.resolve("data/teutonlib/recipe/peripheral_modem.json")))
        canonical.filter { it.startsWith("data/") }.forEach { relative ->
            assertFalse(Files.readString(resources.resolve(relative)).contains("teutonlib:peripheral_modem"), relative)
        }
    }
}
