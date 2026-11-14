package de.TeutonStudio.TeutonLib.integration.train

import com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity
import de.TeutonStudio.TeutonLib.integration.HybridTrainCompatibility
import de.TeutonStudio.TeutonLib.integration.TrainPortalCompatibility
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.WeakHashMap

/** Resume intent belongs to the controller's persistent data, so it follows that
 * controller even when a swivel's nested plot is placed back into the parent. */
object TrainPortalMachines {
    private const val KEY = "teutonlib:portal_machine"
    private const val PENDING = "teutonlib:portal_machines_pending"
    private val firstSeen = WeakHashMap<BlockEntity, Long>()
    private val worldPending = WeakHashMap<ServerLevel, MutableSet<net.minecraft.core.BlockPos>>()
    private val log = LoggerFactory.getLogger("TeutonLib/TrainMachines")
    private val adapters by lazy {
        buildList {
            add(BearingPortalAdapter)
            if (HybridTrainCompatibility.cannonsActive()) add(CannonPortalAdapter)
            if (HybridTrainCompatibility.swivelActive()) add(SwivelPortalAdapter)
        }
    }

    fun prepare(sub: ServerSubLevel): Boolean = prepare(sub, mutableSetOf())

    /** A station assembly creates a new simulation from Create's captured
     * blocks. The per-controller resume record travels in that block's NBT. */
    fun adopt(sub: ServerSubLevel) {
        if (sub.plot.loadedChunks.any { holder -> holder.chunk.blockEntities.values.any { it.persistentData.contains(KEY) } }) {
            val tag = sub.userDataTag?.copy() ?: CompoundTag()
            tag.putBoolean(PENDING, true)
            sub.userDataTag = tag
        }
    }

    private fun prepare(sub: ServerSubLevel, visiting: MutableSet<UUID>): Boolean {
        if (!visiting.add(sub.uniqueId)) return false
        val data = sub.userDataTag?.copy() ?: CompoundTag()
        data.putBoolean(PENDING, true); sub.userDataTag = data
        for (be in sub.plot.loadedChunks.flatMap { it.chunk.blockEntities.values.toList() }) {
            val adapter = adapters.firstOrNull { it.matches(be) && it.running(be) } ?: continue
            val record = CompoundTag().also {
                it.putString("Kind", adapter.kind); it.put("State", adapter.saveState(be))
            }
            be.persistentData.put(KEY, record)
            be.setChanged()
            val child = adapter.child(be)
            if (child != null && !prepare(child, visiting)) return false
            if (child != null && TrainPortalCompatibility.active()) TrainPortalVisuals.beforeHandoff(child)
            try {
                adapter.stop(be)
                if (adapter.running(be)) return false
            } catch (failure: RuntimeException) {
                log.warn("Cannot prepare controller {} on train build {} for a portal: {}", be.blockPos, sub.uniqueId, failure.toString())
                return false
            }
        }
        visiting.remove(sub.uniqueId)
        return true
    }

    /** Cancellation returns prepared controllers to ordinary world chunks. Keep
     * the resume record in their NBT until the world has rebuilt its networks. */
    fun returnedToWorld(level: ServerLevel, positions: Iterable<net.minecraft.core.BlockPos>) {
        for (pos in positions) if (level.getBlockEntity(pos)?.persistentData?.contains(KEY) == true)
            worldPending.getOrPut(level) { mutableSetOf() }.add(pos.immutable())
    }

    fun tickWorld(level: ServerLevel) {
        val pending = worldPending[level] ?: return
        pending.removeIf { pos ->
            if (!level.hasChunkAt(pos)) false
            else {
                val be = level.getBlockEntity(pos)
                if (be == null || !be.persistentData.contains(KEY)) true
                else {
                    val record = be.persistentData.getCompound(KEY)
                    val adapter = adapters.firstOrNull { it.kind == record.getString("Kind") && it.matches(be) }
                    if (adapter == null) false
                    else {
                        val age = level.gameTime - firstSeen.getOrPut(be) { level.gameTime }
                        if (age < 5 || age % 5L != 0L) false
                        else try {
                            if (!adapter.running(be)) adapter.resume(be)
                            if (adapter.running(be) && !record.getBoolean("Restored")) {
                                adapter.restoreState(be, record.getCompound("State"))
                                record.putBoolean("Restored", true); be.setChanged()
                            }
                            if (adapter.running(be) && age >= 10) {
                                be.persistentData.remove(KEY); be.setChanged(); firstSeen.remove(be)
                                true
                            } else false
                        } catch (failure: RuntimeException) {
                            if (age == 5L || age == 100L) log.warn("Cannot resume returned station controller {}: {}", pos, failure.toString())
                            false
                        }
                    }
                }
            }
        }
        if (pending.isEmpty()) worldPending.remove(level)
    }

    fun tick(level: ServerLevel) {
        val container = SubLevelContainer.getContainer(level) ?: return
        for (sub in container.allSubLevels) {
            if (sub.userDataTag?.getBoolean(PENDING) != true) continue
            var pending = false
            for (holder in sub.plot.loadedChunks.toList()) {
                for (pos in holder.chunk.blockEntitiesPos.toList()) {
                    val be = holder.chunk.getBlockEntity(pos) ?: continue
                    val record = be.persistentData.getCompound(KEY)
                    if (!be.persistentData.contains(KEY)) continue
                    val adapter = adapters.firstOrNull { it.kind == record.getString("Kind") && it.matches(be) }
                    if (adapter == null) { be.persistentData.remove(KEY); be.setChanged(); continue }
                    val age = level.gameTime - firstSeen.getOrPut(be) { level.gameTime }
                    if (age < 5 || age % 5L != 0L) { pending = true; continue }
                    try {
                        if (!adapter.running(be)) adapter.resume(be)
                        if (adapter.running(be) && !record.getBoolean("Restored")) {
                            adapter.restoreState(be, record.getCompound("State"))
                            record.putBoolean("Restored", true); be.setChanged()
                        }
                        if (adapter.running(be) && age >= 10) {
                            be.persistentData.remove(KEY); be.setChanged(); firstSeen.remove(be)
                            continue
                        }
                    } catch (failure: RuntimeException) {
                        if (age == 5L || age == 100L) log.warn("Cannot resume controller {} on train build {}: {}", pos, sub.uniqueId, failure.toString())
                    }
                    pending = true
                }
            }
            if (!pending) {
                val data = sub.userDataTag?.copy() ?: continue
                data.remove(PENDING); sub.userDataTag = data
            }
        }
    }
}

private object BearingPortalAdapter : PortalMachineAdapter {
    override val kind = "create:bearing"
    override fun matches(be: BlockEntity) = be is MechanicalBearingBlockEntity || be is ClockworkBearingBlockEntity
    override fun running(be: BlockEntity) = when (be) {
        is MechanicalBearingBlockEntity -> be.isRunning && be.movedContraption?.isAlive == true
        is ClockworkBearingBlockEntity -> be.isRunning
        else -> false
    }
    override fun stop(be: BlockEntity) { when (be) {
        is MechanicalBearingBlockEntity -> be.disassemble()
        is ClockworkBearingBlockEntity -> be.disassemble()
    } }
    override fun resume(be: BlockEntity) { when (be) {
        is MechanicalBearingBlockEntity -> be.assemble()
        is ClockworkBearingBlockEntity -> be.assemble()
    } }
}

/** A returned controller's NBT also survives saving before its delayed resume. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "teutonlib")
object StationWorldMachineResume {
    @net.neoforged.bus.api.SubscribeEvent
    fun loaded(event: net.neoforged.neoforge.event.level.ChunkEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        val chunk = event.chunk as? net.minecraft.world.level.chunk.LevelChunk ?: return
        if (!HybridTrainCompatibility.active() && !TrainPortalCompatibility.active()) return
        // Never inspect Sable's plots as ordinary world chunks.
        level.server.execute {
            if (SubLevelContainer.getContainer(level)?.inBounds(chunk.pos.x, chunk.pos.z) != true)
                TrainPortalMachines.returnedToWorld(level, chunk.blockEntities.keys.toList())
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    fun tick(event: net.neoforged.neoforge.event.tick.ServerTickEvent.Post) {
        if (!HybridTrainCompatibility.active() && !TrainPortalCompatibility.active()) return
        for (level in event.server.allLevels) TrainPortalMachines.tickWorld(level)
    }
}
