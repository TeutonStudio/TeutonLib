package de.TeutonStudio.TeutonLib.integration.train

import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import createtrainfunctionality.LinkRegistry
import createtrainfunctionality.SubLevelLinks
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.createmod.catnip.data.Couple
import com.simibubi.create.content.trains.graph.TrackNodeLocation
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.slf4j.LoggerFactory
import qouteall.imm_ptl.core.compat.IPPortalLookup
import qouteall.imm_ptl.core.compat.create_integration.IPCreateCarriageCrossing
import qouteall.imm_ptl.core.compat.create_integration.IPCreateTrainSides
import qouteall.imm_ptl.core.compat.mixin_create.IPCarriageAccessor
import qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge
import qouteall.imm_ptl.core.compat.sable_integration.IPShipThroughPortalSync
import qouteall.imm_ptl.core.compat.sable_integration.IPShipRiderCarry
import qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleSeats
import qouteall.imm_ptl.core.portal.Mirror
import qouteall.imm_ptl.core.portal.Portal
import java.util.UUID
import java.util.WeakHashMap

/** The train, not the independent ship scanner, owns the handoff of its one live build. */
object TrainPortalTransfers {
    private val log = LoggerFactory.getLogger("TeutonLib/TrainPortals")
    private data class Passage(val portal: Portal, val from: TrackNodeLocation, val to: TrackNodeLocation,
        val sourceEntity: UUID, var handedOff: Boolean = false)
    private val passages = WeakHashMap<Carriage, Passage>()
    private val transferring = ThreadLocal<UUID?>()
    private val coordinated = ThreadLocal<Set<UUID>>()
    private data class Transfer(val old: SubLevelLinks.Link, val target: CarriageContraptionEntity,
        var bound: Boolean = false, var seats: TrainPortalSeats? = null, var riders: TrainPortalRiders? = null,
        var hoses: TrainPortalHoses? = null)
    private val transaction = ThreadLocal<Transfer?>()

    @JvmStatic fun transferring(id: UUID): Boolean = transferring.get() == id || id in coordinated.get().orEmpty()
    internal fun <T> coordinated(ids: Set<UUID>, action: () -> T): T {
        val previous = coordinated.get()
        coordinated.set(previous.orEmpty() + ids)
        return try { action() } finally { if (previous == null) coordinated.remove() else coordinated.set(previous) }
    }
    @JvmStatic fun manages(link: SubLevelLinks.Link): Boolean = CtfPortalAccess.carriage(link)?.let(passages::containsKey) == true
    @JvmStatic fun beforeCaptureRiders() { transaction.get()?.seats?.detach() }
    @JvmStatic fun filterCapturedRiders(captured: IPShipRiderCarry.Captured): IPShipRiderCarry.Captured {
        val transfer = transaction.get() ?: return captured
        val standing = transfer.riders?.filter(captured) ?: captured
        val seated = transfer.seats?.filter(standing) ?: standing
        return transfer.hoses?.filter(seated) ?: seated
    }

    fun link(carriage: Carriage): SubLevelLinks.Link? = SubLevelLinks.links().firstOrNull {
        CtfPortalAccess.carriage(it) === carriage
    }

    fun savedBuild(level: ServerLevel, carriage: Carriage): Boolean {
        if (link(carriage) != null) return true
        val train = carriage.train ?: return false
        val index = train.carriages.indexOf(carriage)
        return level.server.allLevels.any { home -> CtfPortalAccess.entries(home).any {
            it.trainId() == train.id && it.carriageIndex() == index
        } }
    }

    /** Called at the actual portal edge, so null-world shunting never enters this code. */
    fun allow(level: ServerLevel, carriage: Carriage, ends: Couple<TrackNodeLocation>): Boolean {
        val build = link(carriage) ?: return !savedBuild(level, carriage)
        val source = level.server.getLevel(build.dimension) ?: return false
        val sub = SubLevelContainer.getContainer(source)?.getSubLevel(build.subLevelId) as? ServerSubLevel ?: return false
        if (sub.isRemoved) return false
        val existing = passages[carriage]
        if (existing != null && !existing.handedOff) return !existing.portal.isRemoved &&
            ends.either { it == existing.from } && ends.either { it == existing.to }
        val points = listOf(ends.first, ends.second)
        val candidates = points.filter { it.dimension == source.dimension() }.sortedBy {
            it.location.distanceToSqr(Vec3(sub.logicalPose().position().x, sub.logicalPose().position().y, sub.logicalPose().position().z))
        }
        for (from in candidates) {
            val to = points.first { it !== from }
            val portals = IPPortalLookup.touching(source, AABB(from.location, from.location).inflate(1.5)) {
                it !is Mirror && it.teleportable && !it.isRemoved && it.destDim == to.dimension &&
                    kotlin.math.abs(it.scale - 1.0) < 1e-6 && it.transformPoint(from.location).distanceToSqr(to.location) < 16.0
            }
            val portal = portals.minByOrNull { it.getDistanceToNearestPointInPortal(from.location) } ?: continue
            val destination = level.server.getLevel(to.dimension) ?: return false
            // Check the root before the first wheel commits, then release excluded docking links.
            transferring.set(sub.uniqueId)
            val refusal = try { IPSableBridge.whyNotMovable(sub, source, destination) } finally { transferring.remove() }
            if (refusal != null) return false
            TrainPortalDocking.detachExcluded(sub, portal, destination)
            passages[carriage] = Passage(portal, from, to, build.entityId)
            return true
        }
        return false
    }

    /** AutoConvert must not manufacture another plot for IP's second view of the same carriage. */
    @JvmStatic fun secondary(entity: CarriageContraptionEntity): Boolean {
        val carriage = entity.carriage ?: return false
        val linked = link(carriage)
        if (linked != null) return CtfPortalAccess.entity(linked) !== entity
        val level = entity.level() as? ServerLevel ?: return false
        val train = carriage.train ?: return false
        val index = train.carriages.indexOf(carriage)
        for (home in level.server.allLevels) {
            for (entry in CtfPortalAccess.entries(home)) {
                if (entry.trainId() == train.id && entry.carriageIndex() == index) {
                    val sub = SubLevelContainer.getContainer(home)?.getSubLevel(entry.subLevelId()) ?: return true
                    val records = (carriage as IPCarriageAccessor).ip_getEntities().values
                    val canonical = records.mapNotNull { it.entity.get() }.filter { it.level() === home && it.isAlive }
                        .minByOrNull { it.position().distanceToSqr(Vec3(sub.logicalPose().position().x, sub.logicalPose().position().y, sub.logicalPose().position().z)) }
                    return canonical !== entity
                }
            }
        }
        return false
    }

    @JvmStatic fun tick(level: ServerLevel) {
        TrainPortalBearings.tick(level)
        TrainPortalMachines.tick(level)
        TrainPortalDocking.tick(level)
        for (old in SubLevelLinks.links().toList()) {
            if (old.dimension != level.dimension()) continue
            val carriage = CtfPortalAccess.carriage(old) ?: continue
            val sub = SubLevelContainer.getContainer(level)?.getSubLevel(old.subLevelId) as? ServerSubLevel ?: continue
            val owner = CtfPortalAccess.entity(old) as? CarriageContraptionEntity
            val record = owner?.let { IPCreateTrainSides.existingRecordOf(carriage, it) }
            val crossing = if (owner != null && record != null) IPCreateCarriageCrossing.of(owner, record) else null
            // Recover a passage from native IP records after a world reload halfway through.
            if (passages[carriage] == null && crossing != null) {
                val portal = crossing.portal()
                val from = record!!.pivot ?: continue
                passages[carriage] = Passage(portal, from,
                    TrackNodeLocation(portal.transformPoint(from.location)).`in`(portal.destDim), old.entityId)
            }
            val passage = passages[carriage] ?: continue
            if (passage.portal.level() !== level || passage.handedOff) {
                if (crossing == null) passages.remove(carriage)
                continue
            }
            if (passage.portal.isRemoved) { stop(carriage); continue }
            if (crossing == null && owner?.isAlive == true && !carriage.presentInMultipleDimensions() &&
                passage.portal.getDistanceToNearestPointInPortal(owner.position()) > 16.0 + carriage.bogeySpacing) {
                passages.remove(carriage) // reversed away before committing the handoff
                continue
            }
            val destination = level.server.getLevel(passage.portal.destDim) ?: run { stop(carriage); continue }
            val worldPos = sub.logicalPose().position()
            val mapped = passage.portal.transformPoint(Vec3(worldPos.x, worldPos.y, worldPos.z))
            val loadAt = BlockPos.containing(mapped)
            destination.chunkSource.addRegionTicket(TicketType.PORTAL, ChunkPos(loadAt), 3, loadAt)
            val targets = (carriage as IPCarriageAccessor).ip_getEntities().values.mapNotNull { it.entity.get() }
                .filter { it !== owner && it.isAlive && it.level() === destination }
            val target = targets.minByOrNull { it.position().distanceToSqr(mapped) }
            val sourceKey = record?.let { IPCreateTrainSides.keyOfRecord(carriage, it) }
            val sourceGone = sourceKey == null || !IPCreateCarriageCrossing.hasPointIn(carriage, sourceKey)
            val beyond = crossing?.isBeyond(Vec3(worldPos.x, worldPos.y, worldPos.z)) == true
            if (target != null && (beyond || sourceGone)) {
                if (handoff(old, sub, target, passage.portal)) passage.handedOff = true else stop(carriage)
            } else if (target == null && sourceGone) stop(carriage)
        }
    }

    /** Scope survives source removal callbacks and is always cleared, including rollback. */
    fun handoff(old: SubLevelLinks.Link, sub: ServerSubLevel, target: CarriageContraptionEntity, portal: Portal): Boolean {
        val source = sub.level
        val destination = target.level() as? ServerLevel ?: return false
        check(source.server.isSameThread)
        val oldEntity = CtfPortalAccess.entity(old)
        val pos = sub.logicalPose().position()
        val mapped = portal.transformPoint(Vec3(pos.x, pos.y, pos.z))
        val rotation = portal.rotation?.let { Quaterniond(it.x, it.y, it.z, it.w) }
        transferring.set(sub.uniqueId)
        val transfer = Transfer(old, target)
        transaction.set(transfer)
        var docking: TrainPortalDocking.Passage? = null
        try {
            if (!TrainPortalMachines.prepare(sub)) return false
            docking = TrainPortalDocking.prepare(sub, portal, destination) ?: return false
            if (!docking.moveCompanions()) return false
            val hoses = TrainPortalHoses.capture(sub) ?: return false
            transfer.hoses = hoses
            val riders = TrainPortalRiders.capture(sub, portal)
            transfer.riders = riders
            val seats = TrainPortalSeats.capture(sub, portal)
            transfer.seats = seats
            TrainPortalVisuals.beforeHandoff(sub)
            val moved = IPSableBridge.moveThroughPortal(sub, source, destination, mapped, rotation)
            if (moved == null) {
                // IP restores its pre-edit snapshot, including cargo, glue and captured riders.
                // Re-serializing a just-loaded destination plot would lose pending BE data.
                if (oldEntity != null && SubLevelContainer.getContainer(source)?.getSubLevel(old.subLevelId) != null) {
                    SubLevelLinks.linkFor(old.entityId)?.let(CtfPortalAccess::detachLink)
                    check(SubLevelLinks.relink(source, oldEntity, old.subLevelId)) { "Could not bind restored train build ${old.subLevelId}" }
                }
                (SubLevelContainer.getContainer(source)?.getSubLevel(old.subLevelId) as? ServerSubLevel)?.let {
                    riders.restore(it, rollback = true)
                    hoses.restore(it)
                }
                seats.rollback()
                return false
            }
            check(transfer.bound) { "IP returned a train build without its carriage binding" }
            docking.arrived(moved)
            hoses.restore(moved.sub(), portal)
            riders.restore(moved.sub())
            if (source !== destination) CtfPortalAccess.forgetSource(source, sub.uniqueId)
            CtfPortalAccess.unseal(target.uuid)
            IPShipThroughPortalSync.onShipLeftDimension(source.server, sub.uniqueId, source.dimension())
            IPShipThroughPortalSync.onShipArrived(destination, moved.sub())
            // Native straddling seats move ahead of the body via a temporary rider
            // proxy. Complete that lifecycle after arrival, just as IP's controller
            // does, before the next seat tick notices the removed source body.
            IPStraddleSeats.onHandedOff(source, destination, sub.uniqueId, moved)
            seats.arrived(moved)
            log.info("Transferred train build {} {} -> {}; carriage {} -> {}", sub.uniqueId,
                source.dimension().location(), destination.dimension().location(), old.entityId, target.uuid)
            return true
        } finally {
            try { docking?.rollback() } finally { transaction.remove(); transferring.remove() }
        }
    }

    /** Runs inside IP's destination-load try/catch, before glue/riders and journal completion. */
    @JvmStatic fun bindLoaded(level: ServerLevel, loaded: IPSableBridge.Loaded?) {
        val transfer = transaction.get() ?: return
        if (loaded == null) return
        if (loaded.sub().uniqueId != transfer.old.subLevelId) return
        CtfPortalAccess.detachLink(transfer.old)
        try {
            check(SubLevelLinks.relink(level, transfer.target, loaded.sub().uniqueId)) { "CTF refused the transferred build" }
            transfer.bound = true
        } catch (failure: Exception) {
            SubLevelLinks.linkFor(transfer.target.uuid)?.let(CtfPortalAccess::detachLink)
            CtfPortalAccess.restoreLink(transfer.old)
            // Let IP's own catch restore the original pre-edit source snapshot exactly once.
            SubLevelContainer.getContainer(level)!!.removeSubLevel(loaded.sub(),
                dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED)
            throw failure
        }
    }

    fun crossingFor(sub: ServerSubLevel): IPCreateCarriageCrossing.Crossing? {
        val link = SubLevelLinks.linkForSubLevel(sub.uniqueId) ?: return null
        val carriage = CtfPortalAccess.carriage(link) ?: return null
        val owner = CtfPortalAccess.entity(link) as? CarriageContraptionEntity ?: return null
        val record = IPCreateTrainSides.existingRecordOf(carriage, owner) ?: return null
        return IPCreateCarriageCrossing.of(owner, record)
    }

    private fun stop(carriage: Carriage) {
        carriage.blocked = true
        carriage.train?.let { it.speed = 0.0; it.targetSpeed = 0.0 }
    }
}
