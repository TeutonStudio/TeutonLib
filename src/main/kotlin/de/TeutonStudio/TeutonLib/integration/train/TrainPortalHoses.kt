package de.TeutonStudio.TeutonLib.integration.train

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import de.TeutonStudio.TeutonLib.integration.hose.HosePulleyHost
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.portal.DimensionTransition
import net.minecraft.world.phys.Vec3
import qouteall.imm_ptl.core.compat.sable_integration.IPShipRiderCarry
import qouteall.imm_ptl.core.portal.Portal
import java.util.UUID

/** A physical hose end is a world entity, often well below IP's rider capture hull. */
class TrainPortalHoses private constructor(private val source: ServerLevel, private val entries: List<Entry>) {
    private data class Entry(val offset: BlockPos, val id: UUID, val position: Vec3, val velocity: Vec3, val length: Float,
        val passengers: Set<UUID>)
    private val excluded = entries.flatMapTo(hashSetOf()) { it.passengers + it.id }

    fun filter(captured: IPShipRiderCarry.Captured): IPShipRiderCarry.Captured =
        if (excluded.isEmpty()) captured else IPShipRiderCarry.Captured(
            captured.bindings().filterNot { it.uuid() in excluded },
            captured.mounts().filterNot { it.passenger() in excluded },
            captured.seats().filterNot { it.riderUuid() in excluded }, captured.subPosBefore())

    fun restore(sub: ServerSubLevel, portal: Portal? = null) {
        val link = checkNotNull(createtrainfunctionality.SubLevelLinks.linkForSubLevel(sub.uniqueId))
        for (entry in entries) {
            val head = source.server.allLevels.firstNotNullOfOrNull { it.getEntity(entry.id) as? HoseHeadEntity } ?: continue
            val connector = head.connector()
            if (portal != null && connector != null) {
                val parent = Sable.HELPER.getContaining(checkNotNull(connector.level), connector.blockPos) as? ServerSubLevel
                if (parent == null || HybridTrainRuntime.owningRoot(parent)?.uniqueId != sub.uniqueId) connector.release()
            }
            val target = if (portal == null) entry.position else portal.transformPoint(entry.position)
            val velocity = if (portal == null) entry.velocity else portal.transformLocalVecNonScale(entry.velocity)
            val moved = if (head.level() === sub.level) {
                head.teleportTo(target.x, target.y, target.z); head.deltaMovement = velocity; head
            } else head.changeDimension(DimensionTransition(sub.level, target, velocity, head.yRot, head.xRot,
                DimensionTransition.DO_NOTHING)) as? HoseHeadEntity
            checkNotNull(moved) { "Could not transfer hose head ${entry.id}" }
            val owner = link.plotOrigin.offset(entry.offset)
            moved.configure(owner, entry.length)
            moved.worldAnchor()
            val host = sub.level.getBlockEntity(owner) as? HosePulleyHost ?: continue
            host.physicalHeadId = moved.uuid
            host.physicalHeadPosition = moved.blockPosition()
            host.physicalHeadMissingTicks = 0
            (host as HosePulleyBlockEntity).setChanged()
        }
    }

    companion object {
        /** Load a reserved head's chunk before allowing the carriage to commit a handoff. */
        fun capture(sub: ServerSubLevel): TrainPortalHoses? {
            val link = createtrainfunctionality.SubLevelLinks.linkForSubLevel(sub.uniqueId) ?: return null
            val entries = mutableListOf<Entry>()
            for (holder in sub.plot.loadedChunks) for (be in holder.chunk.blockEntities.values.toList()) {
                if (be !is HosePulleyBlockEntity || be !is HosePulleyHost) continue
                val id = be.physicalHeadId ?: continue
                val head = sub.level.getEntity(id) as? HoseHeadEntity
                if (head == null) {
                    be.physicalHeadPosition?.let { pos -> sub.level.chunkSource.addRegionTicket(TicketType.PORTAL, ChunkPos(pos), 2, pos) }
                    return null
                }
                head.configure(be.blockPos, head.hoseLength)
                entries += Entry(be.blockPos.subtract(link.plotOrigin), id, head.position(), head.deltaMovement, head.hoseLength,
                    head.indirectPassengers.mapTo(hashSetOf()) { it.uuid })
            }
            return TrainPortalHoses(sub.level, entries)
        }
    }
}
