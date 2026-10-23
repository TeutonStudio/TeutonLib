package de.TeutonStudio.TeutonLib.integration.computer

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag

object SourceNicknamePersistence {
    private const val LIMIT = 1024

    @JvmStatic
    fun save(bank: SourceNicknameBank): ListTag = ListTag().also { list ->
        bank.entries().entries.take(LIMIT).forEach { (source, nickname) ->
            list.add(CompoundTag().also {
                it.putString("source", source)
                it.putString("nickname", nickname)
            })
        }
    }

    @JvmStatic
    fun load(list: ListTag): SourceNicknameBank = SourceNicknameBank().also { bank ->
        for (i in 0 until minOf(list.size, LIMIT)) {
            val tag = list.getCompound(i)
            try {
                bank.set(tag.getString("source"), tag.getString("nickname"))
            } catch (_: IllegalArgumentException) {
                // Ignore malformed data from copied or manually edited NBT.
            }
        }
    }
}
