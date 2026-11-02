package de.TeutonStudio.TeutonLib.integration.train

import de.TeutonStudio.TeutonLib.integration.HybridTrainCompatibility
import de.TeutonStudio.TeutonLib.integration.cso.bridge.SimulatedDockingAccess as Dock
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.slf4j.LoggerFactory
import qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge
import qouteall.imm_ptl.core.compat.sable_integration.IPShipThroughPortalSync
import qouteall.imm_ptl.core.portal.Portal
import qouteall.imm_ptl.core.portal.shape.RectangularPortalShape
import java.util.UUID
import java.util.WeakHashMap

/** Native docking remains a connection between independent bodies. Only mobile bodies
 * whose entire oriented hull fits the aperture travel with the carriage. */
object TrainPortalDocking {
    private val log = LoggerFactory.getLogger("TeutonLib/TrainDocking")
    internal data class Endpoint(val body: UUID?, val position: BlockPos)
    internal data class Edge(val a: Endpoint, val b: Endpoint)
    private data class Graph(val bodies: List<ServerSubLevel>, val edges: List<Edge>)
    private data class Restore(val edges: List<Edge>, val shifts: Map<UUID, BlockPos>, var attempts: Int = 0)
    private val pending = WeakHashMap<ServerLevel, MutableList<Restore>>()
    private val excluded = WeakHashMap<ServerSubLevel, Portal>()
    private val released = WeakHashMap<ServerLevel, MutableMap<Set<Endpoint>, Portal>>()
    private val restoring = ThreadLocal<Set<Endpoint>>()
    private fun available() = HybridTrainCompatibility.swivelActive() && Dock.available()

    private fun connectors(sub: ServerSubLevel): List<BlockEntity> = sub.plot.loadedChunks.flatMap {
        it.chunk.blockEntities.values.toList()
    }.filter { !it.isRemoved && Dock.isConnector(it) }

    /** Prevent the independent ship scanner from handing a locked partner off before
     * its train. A bounded graph walk also covers chains of docking connectors. */
    fun attachedToTrain(sub: ServerSubLevel): Boolean {
        if (!available()) return false
        val seen = hashSetOf<UUID>()
        val todo = ArrayDeque<ServerSubLevel>().also { it.add(sub) }
        while (todo.isNotEmpty() && seen.size < 256) {
            val body = todo.removeFirst()
            if (!seen.add(body.uniqueId)) continue
            if (HybridTrainRuntime.trainOwned(body)) return true
            for (dock in connectors(body)) {
                if (!Dock.locked(dock)) continue
                val peer = Dock.other(dock) ?: continue
                if (Dock.locked(peer) && Dock.other(peer) === dock) Dock.subLevel(peer)?.let(todo::add)
            }
        }
        return false
    }

    /** All eight oriented plot corners must fit the rectangular aperture. Special
     * shapes are excluded because corners alone cannot detect a hole in the hull. */
    fun fits(sub: ServerSubLevel, portal: Portal): Boolean {
        if (portal.portalShape !is RectangularPortalShape) return false
        val bounds = sub.plot.boundingBox
        if (bounds.maxX() < bounds.minX() || bounds.maxY() < bounds.minY() || bounds.maxZ() < bounds.minZ()) return false
        for (x in listOf(bounds.minX().toDouble(), bounds.maxX() + 1.0))
            for (y in listOf(bounds.minY().toDouble(), bounds.maxY() + 1.0))
                for (z in listOf(bounds.minZ().toDouble(), bounds.maxZ() + 1.0)) {
                    val world = sub.logicalPose().transformPosition(Vec3(x, y, z))
                    val projected = portal.getPointProjectedToPlane(world)
                    if (portal.getDistanceToNearestPointInPortal(projected) > 1e-5) return false
                }
        return true
    }

    private fun endpoint(dock: BlockEntity) = Endpoint(Dock.subLevel(dock)?.uniqueId, dock.blockPos.immutable())
    private fun resolve(level: ServerLevel, endpoint: Endpoint, shifts: Map<UUID, BlockPos>): BlockEntity? {
        if (endpoint.body != null && SubLevelContainer.getContainer(level)?.getSubLevel(endpoint.body) == null) return null
        return level.getBlockEntity(endpoint.position.offset(shifts[endpoint.body] ?: BlockPos.ZERO))
            ?.takeIf { Dock.isConnector(it) && !it.isRemoved }
    }

    private fun graph(root: ServerSubLevel, portal: Portal, destination: ServerLevel): Graph {
        val bodies = linkedMapOf(root.uniqueId to root)
        val edges = linkedMapOf<Set<Endpoint>, Edge>()
        val todo = ArrayDeque<ServerSubLevel>().also { it.add(root) }
        while (todo.isNotEmpty() && bodies.size < 256) {
            val body = todo.removeFirst()
            for (dock in connectors(body)) {
                if (!Dock.locked(dock)) continue
                val peer = Dock.other(dock) ?: continue
                if (!Dock.locked(peer) || Dock.other(peer) !== dock) continue
                val a = endpoint(dock); val b = endpoint(peer)
                edges.putIfAbsent(setOf(a, b), Edge(a, b))
                val other = Dock.subLevel(peer) ?: continue // A world station never moves.
                if (other.uniqueId in bodies || other.isRemoved || HybridTrainRuntime.trainOwned(other) ||
                    StationBuildRuntime.construction(other) || other.massTracker.isInvalid || !fits(other, portal)) continue
                // A body docked to a stationary/world station stays at that station.
                if (connectors(other).any { Dock.locked(it) && Dock.other(it)?.let { peerDock -> Dock.subLevel(peerDock) == null } == true }) continue
                val refusal = TrainPortalTransfers.coordinated(setOf(other.uniqueId)) {
                    IPSableBridge.whyNotMovable(other, root.level, destination)
                }
                if (refusal != null) continue
                bodies[other.uniqueId] = other; todo.add(other)
            }
        }
        return Graph(bodies.values.toList(), edges.values.toList())
    }

    private fun detach(level: ServerLevel, edge: Edge) {
        val a = resolve(level, edge.a, emptyMap())
        val b = resolve(level, edge.b, emptyMap())
        if (a != null) Dock.detach(a)
        if (b != null) Dock.detach(b)
    }

    /** The first wheel reaches the portal edge before the carriage body crosses.
     * Cut excluded links here so a wide station cannot drag the train into its frame. */
    fun detachExcluded(root: ServerSubLevel, portal: Portal, destination: ServerLevel) {
        if (!available()) return
        val graph = graph(root, portal, destination)
        val carried = graph.bodies.mapTo(hashSetOf()) { it.uniqueId }
        for (edge in graph.edges) if (edge.a.body !in carried || edge.b.body !in carried) {
            for (endpoint in listOf(edge.a, edge.b)) if (endpoint.body != null && endpoint.body !in carried) {
                (SubLevelContainer.getContainer(root.level)?.getSubLevel(endpoint.body) as? ServerSubLevel)?.let {
                    if (!fits(it, portal)) excluded[it] = portal
                }
            }
            released.getOrPut(root.level) { mutableMapOf() }[setOf(edge.a, edge.b)] = portal
            detach(root.level, edge)
            log.info("Detached docking connection {} <-> {} before portal {}; external body does not fit or is stationary", edge.a, edge.b, portal.uuid)
        }
    }

    /** Native magnetic pairs otherwise immediately relock two still-adjacent,
     * powered bases after the train's early aperture decision. Let them separate
     * normally, with no lingering magnetic force pulling the excluded body along. */
    fun blocksPair(a: BlockEntity, b: BlockEntity): Boolean {
        val level = a.level as? ServerLevel ?: return false
        val pairs = released[level] ?: return false
        val key = setOf(endpoint(a), endpoint(b))
        val portal = pairs[key] ?: return false
        fun world(dock: BlockEntity) = Dock.subLevel(dock)?.logicalPose()?.transformPosition(dock.blockPos.center) ?: dock.blockPos.center
        if (portal.isRemoved || world(a).distanceToSqr(world(b)) > 64.0) {
            pairs.remove(key)
            if (pairs.isEmpty()) released.remove(level)
            return false
        }
        return true
    }

    /** These endpoints were locked before a common rigid portal transform. Native
     * force=true restores that existing joint even when ordinary gravity sag is
     * larger than the tolerance for making a *new* docking connection. */
    fun restoringPair(a: BlockEntity, b: BlockEntity): Boolean =
        restoring.get()?.let { it == setOf(endpoint(a), endpoint(b)) } == true

    private fun restorePair(a: BlockEntity, b: BlockEntity) {
        if (Dock.other(a) === b && (!Dock.locked(a) || !Dock.locked(b))) {
            Dock.detach(a); Dock.detach(b)
        }
        val previous = restoring.get()
        restoring.set(setOf(endpoint(a), endpoint(b)))
        try { Dock.pair(a, b) } finally { if (previous == null) restoring.remove() else restoring.set(previous) }
    }

    /** A released oversized body keeps its ordinary physics and momentum, but the
     * independent IP scanner may not bypass the train's aperture decision. */
    fun excludedFromPortal(sub: ServerSubLevel): Boolean {
        val portal = excluded[sub] ?: return false
        if (portal.isRemoved || portal.level() !== sub.level || fits(sub, portal) ||
            portal.getDistanceToNearestPointInPortal(IPSableBridge.worldAabb(sub).center) >
                IPSableBridge.worldAabb(sub).size + 16.0) {
            excluded.remove(sub); return false
        }
        return true
    }

    fun prepare(root: ServerSubLevel, portal: Portal, destination: ServerLevel): Passage? {
        if (!available()) return Passage(root, portal, destination, emptyList(), emptyList())
        detachExcluded(root, portal, destination)
        val initial = graph(root, portal, destination)
        val companions = initial.bodies.filter { it !== root }
        for (body in companions) if (!TrainPortalMachines.prepare(body)) return null
        // Stopping native bearings can return their blocks to the companion plot.
        // Recheck the resulting complete hull before selecting the transfer group.
        detachExcluded(root, portal, destination)
        val prepared = graph(root, portal, destination)
        return Passage(root, portal, destination, prepared.bodies.filter { it !== root }, prepared.edges)
    }

    class Passage internal constructor(private val root: ServerSubLevel, private val portal: Portal,
        private val destination: ServerLevel, private val companions: List<ServerSubLevel>, private val edges: List<Edge>) {
        private val source = root.level
        private data class Moved(val body: ServerSubLevel, val originalPosition: Vec3, val shift: BlockPos)
        private val moved = mutableListOf<Moved>()
        private var detached = false
        private var committed = false

        /** Move companions first. If the root's own native transaction fails, each
         * completed companion is moved back before reconnecting the source graph. */
        fun moveCompanions(): Boolean {
            edges.forEach { detach(source, it) }; detached = true
            val rotation = portal.rotation?.let { Quaterniond(it.x, it.y, it.z, it.w) }
            for (body in companions) {
                val p = body.logicalPose().position()
                val original = Vec3(p.x, p.y, p.z)
                TrainPortalVisuals.beforeHandoff(body)
                val result = TrainPortalTransfers.coordinated(setOf(body.uniqueId)) {
                    IPSableBridge.moveThroughPortal(body, source, destination, portal.transformPoint(original), rotation)
                } ?: return false
                moved += Moved(result.sub(), original, result.shift())
                IPShipThroughPortalSync.onShipLeftDimension(source.server, body.uniqueId, source.dimension())
                IPShipThroughPortalSync.onShipArrived(destination, result.sub())
            }
            return true
        }

        fun arrived(rootMove: IPSableBridge.Moved) {
            val shifts = moved.associate { it.body.uniqueId to it.shift }.toMutableMap()
            shifts[root.uniqueId] = rootMove.shift()
            if (edges.isNotEmpty()) pending.getOrPut(destination) { mutableListOf() }.add(Restore(edges, shifts))
            committed = true
            tick(destination)
        }

        fun rollback() {
            if (committed || !detached) return
            val inverseRotation = portal.rotation?.let { Quaterniond(it.x, it.y, it.z, it.w).invert() }
            val shifts = mutableMapOf<UUID, BlockPos>()
            for (entry in moved.asReversed()) {
                val result = TrainPortalTransfers.coordinated(setOf(entry.body.uniqueId)) {
                    IPSableBridge.moveThroughPortal(entry.body, destination, source, entry.originalPosition, inverseRotation)
                }
                checkNotNull(result) { "Could not roll back docked body ${entry.body.uniqueId}; its only live copy remains in ${destination.dimension().location()}" }
                shifts[entry.body.uniqueId] = entry.shift.offset(result.shift())
                IPShipThroughPortalSync.onShipLeftDimension(source.server, entry.body.uniqueId, destination.dimension())
                IPShipThroughPortalSync.onShipArrived(source, result.sub())
            }
            if (edges.isNotEmpty()) pending.getOrPut(source) { mutableListOf() }.add(Restore(edges, shifts))
            tick(source)
        }
    }

    /** Reloaded native BEs may need a few ticks to initialize and extend. Restore
     * using native pairing, rebuilding constraint, fluid, FE, item and CC bridges. */
    fun tick(level: ServerLevel) {
        val restores = pending[level] ?: return
        restores.removeIf { restore ->
            val complete = restore.edges.all { edge ->
                val a = resolve(level, edge.a, restore.shifts)
                val b = resolve(level, edge.b, restore.shifts)
                if (a == null || b == null) false else {
                    if (Dock.other(a) !== b || !Dock.locked(a) || !Dock.locked(b)) restorePair(a, b)
                    Dock.other(a) === b && Dock.other(b) === a && Dock.locked(a) && Dock.locked(b)
                }
            }
            restore.attempts++
            if (!complete && restore.attempts == 100) log.error("Timed out restoring {} native docking connections in {}", restore.edges.size, level.dimension().location())
            complete || restore.attempts >= 100
        }
        if (restores.isEmpty()) pending.remove(level)
    }
}
