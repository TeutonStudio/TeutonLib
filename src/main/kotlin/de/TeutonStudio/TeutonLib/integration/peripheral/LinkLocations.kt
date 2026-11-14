package de.TeutonStudio.TeutonLib.integration.peripheral

import de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.Vec3

/** Last observations only: reading this catalog must never load a chunk. */
class LinkLocations : SavedData() {
    data class Location(val dimension: String, val position: BlockPos, val world: Vec3, val block: String) {
        fun encode() = CompoundTag().also {
            it.putString("dimension", dimension)
            it.putLong("position", position.asLong())
            it.putDouble("x", world.x); it.putDouble("y", world.y); it.putDouble("z", world.z)
            it.putString("block", block)
        }
        companion object {
            fun capture(block: BlockEntity, position: BlockPos = block.blockPos): Location = Location(
                block.level!!.dimension().location().toString(), position.immutable(),
                SubLevelGeometry.projectToWorld(block.level!!, position.center),
                BuiltInRegistries.BLOCK.getKey(block.blockState.block).toString()
            )
            fun decode(tag: CompoundTag) = Location(tag.getString("dimension"), BlockPos.of(tag.getLong("position")),
                Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")), tag.getString("block"))
        }
    }
    data class Entry(val id: UUID, val target: UUID, val nickname: String, val link: Location,
                     val device: Location, val anchor: Location?) {
        fun encode() = CompoundTag().also {
            it.putUUID("id", id); it.putUUID("target", target); it.putString("nickname", nickname)
            it.put("link", link.encode()); it.put("device", device.encode())
            anchor?.let { anchor -> it.put("anchor", anchor.encode()) }
        }
        companion object {
            fun decode(tag: CompoundTag): Entry? {
                if (!tag.hasUUID("id") || !tag.hasUUID("target") || !tag.contains("link") || !tag.contains("device")) return null
                return Entry(tag.getUUID("id"), tag.getUUID("target"), tag.getString("nickname"),
                    Location.decode(tag.getCompound("link")), Location.decode(tag.getCompound("device")),
                    if (tag.contains("anchor")) Location.decode(tag.getCompound("anchor")) else null)
            }
        }
    }
    private val entries = linkedMapOf<UUID, Entry>()
    fun all(): List<Entry> = entries.values.toList()
    fun forTarget(id: UUID): List<Entry> = entries.values.filter { it.target == id }.sortedBy { it.id.toString() }
    fun get(id: UUID): Entry? = entries[id]
    fun put(entry: Entry) { if (entries.put(entry.id, entry) != entry) setDirty() }
    fun remove(id: UUID) { if (entries.remove(id) != null) setDirty() }

    fun observe(link: PeripheralLinkBlockEntity) {
        val level = link.level as? net.minecraft.server.level.ServerLevel ?: return
        val target = link.target() ?: return
        val anchor = LinkRegistry.anchor(level, target)
        val device = Location.capture(link, link.devicePosition()).copy(block =
            level.chunkSource.getChunkNow(link.devicePosition().x shr 4, link.devicePosition().z shr 4)
                ?.let { BuiltInRegistries.BLOCK.getKey(it.getBlockState(link.devicePosition()).block).toString() }
                ?: entries[link.identity()]?.device?.block.orEmpty())
        put(Entry(link.identity(), target, link.nickname(), Location.capture(link), device,
            anchor?.let { Location.capture(it) } ?: entries[link.identity()]?.takeIf { it.target == target }?.anchor))
    }

    fun observeAnchor(anchor: BlockEntity) {
        val id = (anchor as? LinkIdentity)?.`teutonlib$linkTarget`() ?: return
        val location = Location.capture(anchor)
        forTarget(id).forEach { put(it.copy(anchor = location)) }
    }

    override fun save(tag: CompoundTag, provider: HolderLookup.Provider): CompoundTag = write(tag)
    fun write(tag: CompoundTag = CompoundTag()): CompoundTag = tag.also { out ->
        out.put("links", ListTag().also { list -> entries.values.forEach { list.add(it.encode()) } })
    }
    companion object {
        fun read(tag: CompoundTag) = LinkLocations().also { catalog ->
            tag.getList("links", 10).forEach { value -> Entry.decode(value as CompoundTag)?.let { catalog.entries[it.id] = it } }
        }
        fun get(server: MinecraftServer): LinkLocations = server.overworld().dataStorage.computeIfAbsent(
            Factory({ LinkLocations() }, { tag, _ -> read(tag) }, null), "teutonlib_link_locations")
    }
}
