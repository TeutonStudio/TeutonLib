package de.TeutonStudio.TeutonLib.integration.train

import com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity
import createtrainfunctionality.SubLevelLinks
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import org.slf4j.LoggerFactory
import java.util.WeakHashMap

/** IP reassembles bearings before deferred block-entity initialization and kinetic
 * propagation have finished. Record intent in the transferred plot and confirm it
 * again after initialization, including native rollback and server restart. */
object TrainPortalBearings {
    private const val KEY = "teutonlib:resume_bearings"
    private val firstSeen = WeakHashMap<ServerSubLevel, Long>()
    private val log = LoggerFactory.getLogger("TeutonLib/TrainBearings")

    fun capture(sub: ServerSubLevel) {
        val link = SubLevelLinks.linkForSubLevel(sub.uniqueId) ?: return
        val data = sub.userDataTag?.copy() ?: CompoundTag()
        val offsets = data.getList(KEY, 10).map { it as CompoundTag }.map {
            BlockPos(it.getInt("X"), it.getInt("Y"), it.getInt("Z"))
        }.toMutableSet()
        for (holder in sub.plot.loadedChunks) {
            for (be in holder.chunk.blockEntities.values.toList()) {
                val running = when (be) {
                    is MechanicalBearingBlockEntity -> be.isRunning || be.movedContraption != null
                    is ClockworkBearingBlockEntity -> be.isRunning
                    else -> false
                }
                if (running) offsets += be.blockPos.subtract(link.plotOrigin)
            }
        }
        if (offsets.isEmpty()) return
        data.put(KEY, encode(offsets))
        sub.userDataTag = data
    }

    fun tick(level: ServerLevel) {
        val container = SubLevelContainer.getContainer(level) ?: return
        for (sub in container.allSubLevels) {
            val data = sub.userDataTag ?: continue
            if (!data.contains(KEY)) continue
            val link = SubLevelLinks.linkForSubLevel(sub.uniqueId) ?: continue
            val age = level.gameTime - firstSeen.getOrPut(sub) { level.gameTime }
            if (age < 5 || age % 5L != 0L) continue
            val remaining = mutableSetOf<BlockPos>()
            for (entry in data.getList(KEY, 10)) {
                val tag = entry as CompoundTag
                val offset = BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))
                val pos = link.plotOrigin.offset(offset)
                val be = level.getBlockEntity(pos)
                val running = when (be) {
                    is MechanicalBearingBlockEntity -> be.isRunning && be.movedContraption?.isAlive == true
                    is ClockworkBearingBlockEntity -> be.isRunning
                    else -> continue // The player removed this controller in editing mode.
                }
                if (running && age >= 10) continue
                remaining += offset
                if (!running) {
                    try {
                        when (be) {
                            is MechanicalBearingBlockEntity -> be.assemble()
                            is ClockworkBearingBlockEntity -> be.assemble()
                        }
                    } catch (failure: RuntimeException) {
                        if (age == 5L || age == 100L) log.warn("Bearing at {} on train build {} could not resume: {}", pos, sub.uniqueId, failure.toString())
                    }
                    if (age == 100L) log.warn("Bearing at {} on train build {} could not resume after portal transfer; blocks remain in the plot", pos, sub.uniqueId)
                }
            }
            val updated = data.copy()
            if (remaining.isEmpty()) { updated.remove(KEY); firstSeen.remove(sub) }
            else updated.put(KEY, encode(remaining))
            sub.userDataTag = updated
        }
    }

    private fun encode(offsets: Collection<BlockPos>) = ListTag().also { list ->
        offsets.forEach { pos -> list.add(CompoundTag().also {
            it.putInt("X", pos.x); it.putInt("Y", pos.y); it.putInt("Z", pos.z)
        }) }
    }
}
