package de.TeutonStudio.TeutonLib.api.computer.channel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersistentChannelBanksTest {
    @Test
    fun `groups preserve ids targets and encoded format`() {
        var changes = 0
        val first = groupBank { changes++ }
        val group = first.addGroup("flight")
        first.bind(group.id, "pitch", "control:missing-but-stable")
        val encoded = first.encodedDefinitions()

        val restored = groupBank()
        restored.loadEncodedDefinitions(encoded)

        assertEquals(encoded, restored.encodedDefinitions())
        assertEquals(group.id, restored.group("flight").id)
        assertEquals("control:missing-but-stable", restored.group(group.id).bindings.single().targetId)
        assertEquals(2, changes)
    }

    @Test
    fun `signal pulses expire and authority loss clears backend`() {
        var tick = 10L
        var authority = true
        val backend = RecordingBackend()
        val bank = SignalBank(
            clock = { tick }, authority = { authority }, backendFactory = { backend },
            validName = { Regex("[a-z][a-z0-9_-]{0,31}").matches(it) }
        )
        val definition = bank.addChannel("gear")
        bank.tick()
        bank.pulse("gear", 3, 12)
        assertEquals(12, bank.value("gear"))
        tick = 12; bank.tick(); assertEquals(12, bank.value("gear"))
        tick = 13; bank.tick(); assertEquals(0, bank.value("gear"))

        bank.setValue("gear", 8)
        authority = false; bank.tick()
        assertFalse(bank.isOutputEnabled())
        assertEquals(0, bank.value("gear"))
        assertTrue(backend.clears > 0)

        val encoded = bank.encodedDefinitions()
        val restored = SignalBank({ 0 }, { false }, { RecordingBackend() })
        restored.loadEncodedDefinitions(encoded)
        assertEquals(definition.id, restored.definitions().single().id)
        assertEquals(encoded, restored.encodedDefinitions())
        assertThrows(IllegalStateException::class.java) { restored.setValue("gear", 1) }
    }

    private fun groupBank(changed: () -> Unit = {}) = PersistentChannelGroupBank(
        validName = { Regex("[a-z][a-z0-9_-]{0,31}").matches(it) },
        validTarget = { it.startsWith("control:") || it.startsWith("wire:") },
        changed = changed
    )

    private class RecordingBackend : SignalBackend {
        override val name = "recording"
        val values = linkedMapOf<String, Int>()
        var clears = 0
        override fun setValue(channel: String, value: Int) { values[channel] = value }
        override fun removeChannel(channel: String) { values.remove(channel) }
        override fun renameChannel(oldName: String, newName: String, value: Int) {
            values.remove(oldName); values[newName] = value
        }
        override fun connectionCount(channel: String): Int = 0
        override fun clearSignals() { clears++; values.replaceAll { _, _ -> 0 } }
    }
}
