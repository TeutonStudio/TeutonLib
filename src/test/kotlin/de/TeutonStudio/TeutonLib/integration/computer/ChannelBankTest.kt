package de.TeutonStudio.TeutonLib.integration.computer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChannelBankTest {
    @Test
    fun outputRetainsValueAndPrivacyThroughRename() {
        val bank = ChannelBank()
        bank.create("throttle", "output", "")
        assertFalse(bank.get("throttle").published)
        bank.set("throttle", 13)
        bank.published("throttle", true)
        bank.rename("throttle", "engine")
        assertEquals(13, bank.get("engine").value)
        assertTrue(bank.get("engine").published)
        assertThrows(java.lang.IllegalArgumentException::class.java, { bank.get("throttle") })
    }

    @Test
    fun invalidChangesDoNotDestroyDefinition() {
        val bank = ChannelBank()
        bank.create("a", "output", "")
        bank.set("a", 8)
        bank.create("b", "output", "")
        assertThrows(java.lang.IllegalArgumentException::class.java, { bank.rename("a", "b") })
        assertThrows(java.lang.IllegalArgumentException::class.java, { bank.rename("a", "bad/name") })
        assertThrows(java.lang.IllegalArgumentException::class.java, { bank.set("a", 16) })
        assertThrows(java.lang.IllegalArgumentException::class.java, { bank.set("a", -1) })
        assertEquals(8, bank.get("a").value)
        assertEquals(2, bank.entries().size)
    }

    @Test
    fun inputHasExplicitSideAndCannotBeWritten() {
        val bank = ChannelBank()
        assertThrows(java.lang.IllegalArgumentException::class.java, { bank.create("a", "input", "") })
        bank.create("a", "input", "left")
        assertEquals("left", bank.get("a").side)
        assertThrows(java.lang.IllegalArgumentException::class.java, { bank.set("a", 3) })
    }

    @Test
    fun definitionsCanBeRestoredWithoutChangingPrivacyOrOutput() {
        val original = ChannelBank()
        original.create("a", "output", "")
        original.set("a", 15)
        original.published("a", true)
        original.create("b", "input", "top")
        val copy = ChannelBank()
        original.entries()
            .forEach(java.util.function.Consumer { e: de.TeutonStudio.TeutonLib.integration.computer.ChannelBank.Entry ->
                copy.restore(e)
            })
        assertEquals(original.entries(), copy.entries())
    }

    @Test
    fun limitsAndReservedNamesAreEnforced() {
        val bank = ChannelBank()
        assertThrows(java.lang.IllegalArgumentException::class.java, { bank.create("world", "output", "") })
        for (i in 0..63) bank.create("a" + i, "output", "")
        assertThrows(java.lang.IllegalArgumentException::class.java, { bank.create("extra", "output", "") })
        bank.remove("a0")
        bank.create("extra", "output", "")
        assertEquals(64, bank.entries().size)
    }

    @Test
    fun namesAllowUppercaseLettersAndGermanUmlauts() {
        val bank = ChannelBank()
        val names = listOf("HÖHE", "grüße", "STRAßE", "ÄÖÜäöüß")

        names.forEach { bank.create(it, "output", "") }

        assertEquals(names, bank.entries().map { it.name })
        assertThrows(java.lang.IllegalArgumentException::class.java, { bank.create("kanal/überall", "output", "") })
    }
}
