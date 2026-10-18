package de.TeutonStudio.TeutonLib.integration.cso.bridge

import com.simibubi.create.content.kinetics.base.IRotate
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual
import dev.ryanhcode.sable.Sable
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import net.minecraft.world.level.Level
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.HashMap
import java.util.HashSet

/** Client-only mirror of virtual edges, used solely for tooth-phase rendering. */
object ClientGearBridgeManager {
    private val byDock = HashMap<DockKey, List<Edge>>()
    private val lastRefresh = HashMap<DockKey, Long>()
    @Volatile private var corrections: Map<BlockPos, Float> = emptyMap()
    private var correctedGears: Set<KineticBlockEntity> = emptySet()
    private val calculating = ThreadLocal.withInitial { false }
    fun clear() {
        byDock.clear()
        lastRefresh.clear()
        rebuild()
    }
    fun removeLevel(level: Level) {
        byDock.keys.removeIf { it.belongsTo(level) }
        lastRefresh.keys.removeIf { it.belongsTo(level) }
        rebuild()
    }
    fun tickDock(connector: BlockEntity) {
        val level = connector.level ?: return
        if (!level.isClientSide) return
        val other = SimulatedDockingAccess.other(connector)
        if (other == null || !SimulatedDockingAccess.locked(connector) || !SimulatedDockingAccess.locked(other)) { removeForConnector(connector); return }
        refresh(connector, other)
    }
    fun removeForConnector(connector: BlockEntity) {
        val changed = byDock.keys.removeIf { it.contains(connector) }; lastRefresh.keys.removeIf { it.contains(connector) }
        if (changed) rebuild()
    }
    fun onGearRemoved(gear: KineticBlockEntity) {
        var changed = false
        byDock.entries.forEach { entry -> val filtered = entry.value.filter { it.from.gear !== gear && it.to.gear !== gear }; if (filtered.size != entry.value.size) { entry.setValue(filtered); changed = true } }
        if (changed) rebuild()
    }
    fun phaseCorrection(position: Vec3i): Float = if (calculating.get()) 0f else corrections[BlockPos(position.x, position.y, position.z)] ?: 0f
    private fun refresh(a: BlockEntity, b: BlockEntity) {
        val level = a.level ?: return
        if (level !== b.level) { removeForConnector(a); return }
        val key = DockKey(a, b); val epoch = level.gameTime / 10L
        if (lastRefresh.put(key, epoch) == epoch) return
        val sideA = Sable.HELPER.getContainingClient(a); val sideB = Sable.HELPER.getContainingClient(b)
        if (sideA === sideB) { lastRefresh.remove(key); if (byDock.remove(key) != null) rebuild(); return }
        val left = ArrayList<GearPose>(); val right = ArrayList<GearPose>()
        ClientGearRegistry.snapshot(level).mapNotNull(GearPose::ofClient).forEach { if (it.subLevel === sideA) left += it else if (it.subLevel === sideB) right += it }
        val buckets = HashMap<SpatialKey, MutableList<GearPose>>(); right.forEach { buckets.getOrPut(SpatialKey.of(it)) { ArrayList() } += it }
        val edges = ArrayList<Edge>()
        left.forEach { from ->
            val origin = SpatialKey.of(from)
            for (x in -1..1) for (y in -1..1) for (z in -1..1) buckets[origin.offset(x, y, z)]?.forEach { to ->
                val dx = to.center.x - from.center.x; val dy = to.center.y - from.center.y; val dz = to.center.z - from.center.z
                if (dx * dx + dy * dy + dz * dz in .60..2.55 && GearGeometry.modifier(from, to) != 0f && GearGeometry.modifier(to, from) != 0f) { edges += Edge(from, to); edges += Edge(to, from) }
            }
        }
        val previous = byDock.put(key, edges)
        if (previous == null || previous.toSet() != edges.toSet()) rebuild()
    }
    private fun rebuild() {
        val next = HashMap<BlockPos, Float>()
        val edges = byDock.values.flatten().toSet()
        calculating.set(true)
        try { edges.forEach { addCorrection(next, it.from) } }
        finally { calculating.set(false) }
        val nextGears = edges.map { it.from.gear }.toSet()
        val affected = correctedGears + nextGears
        val previous = corrections
        corrections = next.toMap()
        correctedGears = nextGears
        affected.filter { previous[it.blockPos] != next[it.blockPos] }.forEach { gear ->
            val level = gear.level
            if (!gear.isRemoved && level != null && VisualizationManager.supportsVisualization(level)) {
                val visuals = VisualizationManager.get(level)?.blockEntities()
                // rotationOffset is captured when a visual is constructed, so recreate it.
                visuals?.queueRemove(gear)
                visuals?.queueAdd(gear)
            }
        }
    }
    private fun addCorrection(target: MutableMap<BlockPos, Float>, pose: GearPose) {
        val globalAxis = cardinalAxis(pose.axis) ?: return
        val gear = pose.gear; val state = gear.blockState
        val desired = KineticBlockEntityVisual.rotationOffset(state, globalAxis, BlockPos.containing(pose.center))
        val localAxis = (state.block as IRotate).getRotationAxis(state)
        val local = KineticBlockEntityVisual.rotationOffset(state, localAxis, gear.blockPos)
        target[gear.blockPos.immutable()] = desired - local
    }
    private fun cardinalAxis(v: org.joml.Vector3d): Direction.Axis? {
        val x = kotlin.math.abs(v.x); val y = kotlin.math.abs(v.y); val z = kotlin.math.abs(v.z)
        return if (x >= y && x >= z && x >= .9925) Direction.Axis.X else if (y >= z && y >= .9925) Direction.Axis.Y else if (z >= .9925) Direction.Axis.Z else null
    }
    private data class Edge(val from: GearPose, val to: GearPose)
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
