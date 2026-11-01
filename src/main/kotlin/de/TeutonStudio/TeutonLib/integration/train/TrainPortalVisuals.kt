package de.TeutonStudio.TeutonLib.integration.train

import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.api.SubLevelHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import qouteall.imm_ptl.core.compat.sable_straddle.IPStraddle
import qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge
import qouteall.imm_ptl.core.compat.sable_integration.IPShipThroughPortalSync
import qouteall.imm_ptl.core.compat.IPPortalLookup
import qouteall.imm_ptl.core.portal.Mirror
import qouteall.imm_ptl.core.portal.Portal
import java.util.UUID
import java.util.WeakHashMap

/** Use IP's own clipping, image colliders and sync, while excluding its independent ship handoff. */
object TrainPortalVisuals {
    private val sessions = Class.forName("qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleSessions")
    private val keyType = Class.forName("qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleSessions\$Key")
    private val keyConstructor = keyType.getDeclaredConstructor(UUID::class.java, UUID::class.java).also { it.isAccessible = true }
    private val open = sessions.getDeclaredMethod("openOrRefresh", ServerSubLevel::class.java, Portal::class.java,
        ServerLevel::class.java, Vec3::class.java, Long::class.javaPrimitiveType).also { it.isAccessible = true }
    private val keys = sessions.getDeclaredMethod("keys").also { it.isAccessible = true }
    private val ship = keyType.getDeclaredMethod("ship").also { it.isAccessible = true }
    private val close = sessions.getDeclaredMethod("close", keyType, String::class.java).also { it.isAccessible = true }
    private val sync = Class.forName("qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleSync")
    private val handedOff = sync.getDeclaredMethod("markHandedOff", keyType).also { it.isAccessible = true }
    private val known = sync.getDeclaredField("KNOWN").also { it.isAccessible = true }
    private val syncedPlots = IPShipThroughPortalSync::class.java.getDeclaredField("SYNCED").also { it.isAccessible = true }
    private val fullSync = IPShipThroughPortalSync::class.java.getDeclaredMethod("sendStartTracking",
        ServerPlayer::class.java, ServerLevel::class.java, ServerSubLevel::class.java).also { it.isAccessible = true }
    private val viewerDimensions = WeakHashMap<ServerPlayer, ResourceKey<Level>>()

    private class View {
        var searchedAt = Long.MIN_VALUE
        var searchedBox: AABB? = null
        var nearby: List<Portal> = emptyList()
        var portal: Portal? = null
        var key: Any? = null
        var side = 1.0
    }
    private val views = WeakHashMap<ServerSubLevel, View>()
    private val activeViews = WeakHashMap<ServerLevel, MutableSet<ServerSubLevel>>()
    private data class Dependencies(val tick: Int, val subLevels: Set<ServerSubLevel>)
    private val dependencies = WeakHashMap<ServerLevel, Dependencies>()

    private fun active(level: ServerLevel, sub: ServerSubLevel, on: Boolean) {
        if (on) {
            if (activeViews.getOrPut(level) { mutableSetOf() }.add(sub)) dependencies.remove(level)
        } else activeViews[level]?.let {
            if (it.remove(sub)) dependencies.remove(level)
            if (it.isEmpty()) activeViews.remove(level)
        }
    }

    private fun supported(level: ServerLevel, portal: Portal) = portal.level() === level &&
        !portal.isRemoved && portal !is Mirror && portal.teleportable && kotlin.math.abs(portal.scale - 1.0) < 1e-6

    @Suppress("UNCHECKED_CAST")
    fun refresh(level: ServerLevel, sub: ServerSubLevel, tick: Long, seen: Set<*>) {
        if (!IPStraddle.isAvailable()) return
        val box = sub.plot.boundingBox
        if (box === BoundingBox3i.EMPTY) return
        val pose = sub.logicalPose()
        val center = pose.transformPosition(Vec3((box.minX() + box.maxX() + 1.0) * .5,
            (box.minY() + box.maxY() + 1.0) * .5, (box.minZ() + box.maxZ() + 1.0) * .5))
        val halfX = pose.transformNormal(Vec3((box.maxX() - box.minX() + 1.0) * .5, 0.0, 0.0))
        val halfY = pose.transformNormal(Vec3(0.0, (box.maxY() - box.minY() + 1.0) * .5, 0.0))
        val halfZ = pose.transformNormal(Vec3(0.0, 0.0, (box.maxZ() - box.minZ() + 1.0) * .5))
        fun touches(portal: Portal) = supported(level, portal) && TrainPortalClip.touches(center, halfX, halfY, halfZ,
            portal.originPos, portal.normal, portal.axisW, portal.axisH, portal.width * .5, portal.height * .5)

        val view = views.getOrPut(sub) { View() }
        var portal = view.portal?.takeIf(::touches)
        if (portal == null) {
            val hull = IPSableBridge.worldAabb(sub)
            val searched = view.searchedBox
            // A cached miss is safe while the body stays inside the expanded query.
            // Moving quickly out of it triggers an immediate query, independent of cadence.
            if (searched == null || tick < view.searchedAt || tick - view.searchedAt >= 5 ||
                !searched.contains(hull.minX, hull.minY, hull.minZ) || !searched.contains(hull.maxX, hull.maxY, hull.maxZ)) {
                view.searchedBox = hull.inflate(2.0)
                view.searchedAt = tick
                view.nearby = IPPortalLookup.touching(level, view.searchedBox!!) { supported(level, it) }
            }
            portal = view.nearby.firstOrNull {
                val position = pose.position()
                it.getDistanceToPlane(Vec3(position.x(), position.y(), position.z())) >= -0.75 && touches(it)
            }
            view.side = 1.0
        }
        // The rail record becomes authoritative when the first wheel crosses. It can
        // correct an early generic face choice without another spatial portal query.
        if (portal != null || view.nearby.isNotEmpty()) {
            val crossing = TrainPortalTransfers.crossingFor(sub)
            val railPortal = crossing?.portal()?.takeIf(::touches)
            if (railPortal != null) {
                portal = railPortal
                view.side = if (crossing.keepNormal().dot(railPortal.normal) < 0.0) -1.0 else 1.0
            }
        }
        if (view.portal !== portal) {
            view.key?.let { close.invoke(null, it, "train build clear of portal") }
            active(level, sub, false)
            view.portal = portal
            view.key = portal?.let { keyConstructor.newInstance(sub.uniqueId, it.uuid) }
        }
        if (portal != null && open.invoke(null, sub, portal, level, portal.normal.scale(-view.side), tick) == true) {
            active(level, sub, true)
            (seen as MutableSet<Any>).add(view.key!!)
        }
    }

    /** Match IP's own handoff so clients keep the old mesh until its replacement is compiled. */
    fun beforeHandoff(sub: ServerSubLevel) {
        val id = sub.uniqueId
        for (key in keys.invoke(null) as List<*>) {
            if (ship.invoke(key) == id) {
                handedOff.invoke(null, key)
                close.invoke(null, key, "train-owned handoff")
            }
        }
        views.remove(sub)
        active(sub.level, sub, false)
        IPShipThroughPortalSync.resendLastPose(sub.level, sub)
    }

    /** Ephemeral visibility dependency, never a saved force-load ticket. */
    fun visibleDependencies(level: ServerLevel, original: Collection<ServerSubLevel>): Collection<ServerSubLevel> {
        val visible = activeViews[level] ?: return original
        // The command closes native sessions without calling the controller reset.
        if (!IPStraddle.isAvailable()) {
            reset()
            return original
        }
        // Sable already expands native force-loaded bodies to their full dependency
        // chain. Reuse that result instead of traversing their actors a second time.
        if (visible.all { it.isRemoved || original.contains(it) }) return original
        val tick = level.server.tickCount
        var cached = dependencies[level]
        if (cached == null || cached.tick != tick) {
            val required = mutableSetOf<ServerSubLevel>()
            for (sub in visible) {
                if (sub.isRemoved) continue
                var remoteViewer = false
                IPStraddle.forEachFarHalfViewer(sub) { if (!it.isRemoved) remoteViewer = true }
                if (remoteViewer) required.addAll(SubLevelHelper.getLoadingDependencyChain(sub))
            }
            cached = Dependencies(tick, java.util.Collections.unmodifiableSet(required))
            dependencies[level] = cached
        }
        val required = cached.subLevels
        if (required.isEmpty() || original.containsAll(required)) return original
        if (original.isEmpty()) return required
        return original.toMutableSet().also { it.addAll(required) }
    }

    @JvmStatic fun removed(sub: ServerSubLevel) {
        views.remove(sub)
        active(sub.level, sub, false)
    }

    /** Reuse IP's player loop; a vanilla dimension switch clears client render sessions. */
    @JvmStatic fun syncViewer(player: ServerPlayer) {
        if (views.isEmpty()) return
        val dimension = player.level().dimension()
        val previous = viewerDimensions.put(player, dimension)
        if (previous != null && previous != dimension) {
            // Re-announce once after the client discarded its previous world. Ordinary
            // ticks keep IP's version-based delta sync and never repeat a full begin.
            (known.get(null) as MutableMap<*, *>).remove(player.uuid)
            // IP can adopt Sable's old tracking entry even though a vanilla client
            // world switch discarded the plot. Let its next rescan send it again.
            (syncedPlots.get(null) as MutableMap<*, *>).remove(player.uuid)
            // Sable may already have adopted the remote tracking entry earlier in
            // this tick. Repair only locally tracked, currently straddling builds;
            // otherwise its normal tracking pass will supply the initial snapshot.
            activeViews[player.serverLevel()]?.forEach { sub ->
                if (!sub.isRemoved && sub.trackingPlayers.contains(player.uuid)) {
                    fullSync.invoke(null, player, sub.level, sub)
                }
            }
            dependencies.clear()
        }
    }

    @JvmStatic fun reset() { views.clear(); activeViews.clear(); dependencies.clear(); viewerDimensions.clear() }
}
