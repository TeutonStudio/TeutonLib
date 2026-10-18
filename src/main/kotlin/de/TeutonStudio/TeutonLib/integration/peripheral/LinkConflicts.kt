package de.TeutonStudio.TeutonLib.integration.peripheral

import java.lang.IllegalArgumentException
import java.util.HashSet
import java.util.UUID
import java.util.function.BiFunction
import java.util.function.Supplier
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/** A cloned anchor stays disabled even after one of the copies unloads or the server restarts.  */
class LinkConflicts : SavedData() {
    private val conflicts: MutableSet<UUID> = HashSet<UUID>()
    fun add(id: UUID) {
        if (conflicts.add(id)) setDirty()
    }

    fun contains(id: UUID): Boolean {
        return conflicts.contains(id)
    }

    override fun save(tag: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        conflicts.stream().map<String> { obj: UUID? -> obj.toString() }.sorted()
            .forEach { value: String? -> list.add(StringTag.valueOf(value)) }
        tag.put("targets", list)
        return tag
    }

    companion object {
        @JvmStatic
        fun get(server: MinecraftServer): LinkConflicts {
            return server.overworld().dataStorage.computeIfAbsent<LinkConflicts>(
                SavedData.Factory<LinkConflicts>(
                    java.util.function.Supplier { LinkConflicts() },
                    BiFunction { tag: CompoundTag, provider: HolderLookup.Provider? ->
                        LinkConflicts.Companion.load(
                            tag,
                            provider
                        )
                    },
                    null
                ), "teutonlib_link_conflicts"
            )
        }

        @JvmStatic
        private fun load(tag: CompoundTag, provider: HolderLookup.Provider?): LinkConflicts {
            val result = LinkConflicts()
            for (value in tag.getList("targets", 8)) {
                try {
                    result.conflicts.add(UUID.fromString(value.asString))
                } catch (ignored: IllegalArgumentException) {
                }
            }
            return result
        }
    }
}
