package de.TeutonStudio.TeutonLib.integration.peripheral

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

/** Persistent absolute block faces; every non-empty subset of the six faces is supported. */
object LinkAntennaSides {
    const val ALL = 63
    fun bit(side: Direction): Int = 1 shl side.get3DDataValue()
    fun valid(mask: Int): Boolean = mask in 1..ALL
    fun validate(mask: Int): Int {
        require(mask != 0) { "teutonlib.antenna.minimum_sides" }
        require(valid(mask)) { "teutonlib.antenna.invalid_sides" }
        return mask
    }
    fun directions(mask: Int): List<Direction> = Direction.entries.filter { mask and bit(it) != 0 }
    fun read(tag: CompoundTag): Int {
        if (tag.contains("TeutonLibAntennaSides", Tag.TAG_INT.toInt())) {
            val mask = tag.getInt("TeutonLibAntennaSides")
            if (valid(mask)) return mask
        }
        val legacy = Direction.entries.firstOrNull { it.name == tag.getString("TeutonLibAntennaSide") } ?: Direction.UP
        return bit(legacy)
    }
    fun write(tag: CompoundTag, mask: Int) {
        validate(mask)
        tag.putInt("TeutonLibAntennaSides", mask)
        // Keep the old single-face field readable by existing integrations and older saves.
        tag.putString("TeutonLibAntennaSide", directions(mask).first().name)
    }
}
