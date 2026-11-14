package de.TeutonStudio.TeutonLib.integration.peripheral

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LinkAntennaSidesTest {
    @Test fun everyNonemptySubsetRoundTripsAndContainsExactlyItsSelectedFaces() {
        (1..63).forEach { mask ->
            assertEquals(Integer.bitCount(mask), LinkAntennaSides.directions(mask).size)
            assertEquals(mask, LinkAntennaSides.directions(mask).fold(0) { out, side -> out or LinkAntennaSides.bit(side) })
            val tag = CompoundTag()
            LinkAntennaSides.write(tag, mask)
            assertEquals(mask, LinkAntennaSides.read(tag))
        }
    }
    @Test fun invalidOrEmptySelectionsCannotReplaceTheSavedSelection() {
        val tag = CompoundTag()
        LinkAntennaSides.write(tag, 63)
        listOf(0, -1, 64, Int.MAX_VALUE).forEach { invalid ->
            assertFalse(LinkAntennaSides.valid(invalid))
            assertThrows(IllegalArgumentException::class.java) { LinkAntennaSides.write(tag, invalid) }
            assertEquals(63, LinkAntennaSides.read(tag))
        }
    }
    @Test fun oldWorldsKeepTheirMountFaceAndInvalidSavedMasksRecoverSafely() {
        assertEquals(LinkAntennaSides.bit(Direction.UP), LinkAntennaSides.read(CompoundTag()))
        Direction.entries.forEach { side ->
            val tag = CompoundTag().also { it.putString("TeutonLibAntennaSide", side.name) }
            assertEquals(LinkAntennaSides.bit(side), LinkAntennaSides.read(tag))
            tag.putInt("TeutonLibAntennaSides", 0)
            assertEquals(LinkAntennaSides.bit(side), LinkAntennaSides.read(tag))
        }
    }
}
