package de.TeutonStudio.TeutonLib.integration.computer

import kotlin.math.*
import java.lang.IllegalArgumentException
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag

object ChannelPersistence {
    @JvmStatic
    fun save(bank: ChannelBank): ListTag {
        val list = ListTag()
        for (e in bank.entries()) {
            val t = CompoundTag()
            t.putString("name", e.name)
            t.putString("direction", e.direction)
            t.putString("side", e.side)
            t.putBoolean("public", e.published)
            t.putInt("value", e.value)
            list.add(t)
        }
        return list
    }

    @JvmStatic
    fun load(list: ListTag): ChannelBank {
        val bank = ChannelBank()
        for (i in 0..<min(list.size, ChannelBank.Companion.LIMIT)) {
            val t = list.getCompound(i)
            try {
                bank.restore(
                    ChannelBank.Entry(
                        t.getString("name"),
                        t.getString("direction"),
                        t.getString("side"),
                        t.getBoolean("public"),
                        t.getInt("value")
                    )
                )
            } catch (ignored: IllegalArgumentException) { /* Ignore invalid definitions from copied/older NBT. */
            }
        }
        return bank
    }
}
