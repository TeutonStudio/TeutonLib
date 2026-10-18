package de.TeutonStudio.TeutonLib.integration.peripheral;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** A cloned anchor stays disabled even after one of the copies unloads or the server restarts. */
final class LinkConflicts extends SavedData {
    private final Set<UUID> conflicts = new HashSet<>();
    static LinkConflicts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(LinkConflicts::new, LinkConflicts::load, null), "teutonlib_link_conflicts");
    }
    private static LinkConflicts load(CompoundTag tag, HolderLookup.Provider provider) {
        var result = new LinkConflicts();
        for (var value : tag.getList("targets", 8)) {
            try { result.conflicts.add(UUID.fromString(value.getAsString())); } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
    void add(UUID id) { if (conflicts.add(id)) setDirty(); }
    boolean contains(UUID id) { return conflicts.contains(id); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        var list = new ListTag();
        conflicts.stream().map(UUID::toString).sorted().forEach(value -> list.add(StringTag.valueOf(value)));
        tag.put("targets", list);
        return tag;
    }
}
