package de.TeutonStudio.TeutonLib.integration.hose

import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap
import net.minecraft.world.level.Level

/** Loaded identities survive Sable assembly relocation; conflicting copied UUIDs fail closed. */
object HoseConnectorRegistry {
    private val entries = Collections.newSetFromMap(WeakHashMap<HoseDrainConnection, Boolean>())
    fun clear() = entries.clear()
    fun register(connector: HoseDrainConnection) { entries.add(connector) }
    fun find(world: Level, id: UUID, head: UUID): HoseDrainConnection? = entries.filter {
        it.level === world && !it.isRemoved && it.identity == id && it.headId == head &&
            HosePipeGraph.loaded(world, it.blockPos) === it.drain
    }.singleOrNull()
}
