package de.TeutonStudio.TeutonLib.integration.computer

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SourceNicknamePersistenceTest {
    @Test
    fun `nicknames are trimmed persisted and removable without changing addresses`() {
        val bank = SourceNicknameBank()
        bank.set("left", "  Navigation  ")
        bank.set("modem_42", "Engine room")

        val loaded = SourceNicknamePersistence.load(SourceNicknamePersistence.save(bank))
        assertEquals("Navigation", loaded.get("left"))
        assertEquals("Engine room", loaded.get("modem_42"))

        loaded.set("left", "   ")
        assertNull(loaded.get("left"))
        assertEquals("Engine room", loaded.get("modem_42"))
    }

    @Test
    fun `invalid copied nbt is ignored`() {
        val list = ListTag().also { list ->
            list.add(CompoundTag().also { it.putString("source", ""); it.putString("nickname", "broken") })
            list.add(CompoundTag().also { it.putString("source", "right"); it.putString("nickname", "Valid") })
        }

        val loaded = SourceNicknamePersistence.load(list)
        assertEquals(mapOf("right" to "Valid"), loaded.entries())
        assertThrows(IllegalArgumentException::class.java) { loaded.set("right", "x".repeat(33)) }
    }
}
