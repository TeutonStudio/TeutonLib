package de.TeutonStudio.TeutonLib.integration.cso.bridge

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.Level
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.IdentityHashMap

object GearBridgeManager {
    private val byDock = HashMap<DockKey, List<DirectedEdge>>()
    private val lastRefresh = HashMap<DockKey, Long>()
    private var active: List<DirectedEdge> = emptyList()
    data class DirectedEdge(val from: KineticBlockEntity, val to: KineticBlockEntity, val modifier: Float)
    fun clear() {
        byDock.clear()
        lastRefresh.clear()
        rebuildActive() // Keep the old virtual neighbours during detach, replace, then attach.
    }
    fun removeLevel(level: Level) {
        byDock.keys.removeIf { it.belongsTo(level) }
        lastRefresh.keys.removeIf { it.belongsTo(level) }
        rebuildActive()
    }
    fun tickDock(connector: BlockEntity) {
        val level = connector.level ?: return
        if (level.isClientSide) return
        val other = SimulatedDockingAccess.other(connector)
        if (other == null || !SimulatedDockingAccess.locked(connector) || !SimulatedDockingAccess.locked(other)) { removeForConnector(connector); return }
        refresh(connector, other)
    }
    fun removeForConnector(connector: BlockEntity) {
        val changed = byDock.keys.removeIf { it.contains(connector) }
        lastRefresh.keys.removeIf { it.contains(connector) }
        if (changed) rebuildActive()
    }
    fun onGearRemoved(gear: KineticBlockEntity) {
        var changed = false
        byDock.entries.forEach { entry ->
            val filtered = entry.value.filter { it.from !== gear && it.to !== gear }
            if (filtered.size != entry.value.size) { entry.setValue(filtered); changed = true }
        }
        if (changed) rebuildActive()
    }
    private fun refresh(a: BlockEntity, b: BlockEntity) {
        val level = a.level ?: return
        if (level !== b.level) { removeForConnector(a); return }
        val key = DockKey(a, b); val epoch = level.gameTime / 10L
        if (lastRefresh.put(key, epoch) == epoch) return
        val sideA = Sable.HELPER.getContaining(a); val sideB = Sable.HELPER.getContaining(b)
        if (sideA === sideB) { lastRefresh.remove(key); if (byDock.remove(key) != null) rebuildActive(); return }
        val left = ArrayList<GearPose>(); val right = ArrayList<GearPose>()
        GearRegistry.snapshot(level).mapNotNull(GearPose::of).forEach { if (it.subLevel === sideA) left += it else if (it.subLevel === sideB) right += it }
        val buckets = HashMap<SpatialKey, MutableList<GearPose>>(); right.forEach { buckets.getOrPut(SpatialKey.of(it)) { ArrayList() } += it }
        val edges = ArrayList<DirectedEdge>()
        left.forEach { from ->
            val origin = SpatialKey.of(from)
            for (x in -1..1) for (y in -1..1) for (z in -1..1) buckets[origin.offset(x, y, z)]?.forEach { to ->
                val dx = to.center.x - from.center.x; val dy = to.center.y - from.center.y; val dz = to.center.z - from.center.z
                val distance = dx * dx + dy * dy + dz * dz
                if (distance in .60..2.55) {
                    val forward = GearGeometry.modifier(from, to); val backward = GearGeometry.modifier(to, from)
                    if (forward != 0f && backward != 0f) { edges += DirectedEdge(from.gear, to.gear, forward); edges += DirectedEdge(to.gear, from.gear, backward) }
                }
            }
        }
        val previous = byDock.put(key, edges)
        if (previous == null || previous.toSet() != edges.toSet()) rebuildActive()
    }
    private fun rebuildActive() {
        val next = byDock.values.flatten().distinct(); val oldSet = active.toSet(); val newSet = next.toSet()
        if (oldSet == newSet) return
        val changed = Collections.newSetFromMap(IdentityHashMap<KineticBlockEntity, Boolean>())
        oldSet.filter { it !in newSet }.forEach { changed += it.from; changed += it.to }
        newSet.filter { it !in oldSet }.forEach { changed += it.from; changed += it.to }
        changed.filter(::valid).forEach { it.detachKinetics() }
        BridgeRegistry.replace(next); active = next
        changed.filter(::valid).forEach { gear ->
            if (gear.hasSource()) {
                gear.source?.let { source ->
                    val stillVirtual = BridgeRegistry.neighbours(gear).any { it.blockPos == source }
                    val removedVirtual = oldSet.any { it.from === gear && it.to.blockPos == source && it !in newSet }
                    if (removedVirtual && !stillVirtual) gear.removeSource()
                }
            }
        }
        changed.filter(::valid).forEach { it.attachKinetics() }
    }
    private fun valid(gear: KineticBlockEntity?) = gear != null && !gear.isRemoved && gear.level != null && !gear.level!!.isClientSide
    private data class SpatialKey(val x: Int, val y: Int, val z: Int) {
        companion object { fun of(pose: GearPose) = SpatialKey(kotlin.math.floor(pose.center.x / 2.0).toInt(), kotlin.math.floor(pose.center.y / 2.0).toInt(), kotlin.math.floor(pose.center.z / 2.0).toInt()) }
        fun offset(dx: Int, dy: Int, dz: Int) = SpatialKey(x + dx, y + dy, z + dz)
    }
    private class DockKey(private val a: BlockEntity, private val b: BlockEntity) {
        fun belongsTo(level: Level) = a.level === level || b.level === level
        fun contains(value: BlockEntity) = a === value || b === value
        override fun equals(other: Any?) = other is DockKey && ((a === other.a && b === other.b) || (a === other.b && b === other.a))
        override fun hashCode() = System.identityHashCode(a) xor System.identityHashCode(b)
    }
}
