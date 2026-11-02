package de.TeutonStudio.TeutonLib.integration.hybrid

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock
import com.simibubi.create.content.trains.station.StationBlockEntity
import de.TeutonStudio.TeutonLib.ServerConfig
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.WeakHashMap

/** Construction plots own the real blocks, even before a Create train exists.
 * Only the synchronous assembly call temporarily exposes them to Create's native
 * capture/storage logic. Failed assembly returns the same contents to the plots. */
object StationBuildRuntime {
    const val KEY = "teutonlib:station_build"
    private const val STATION = "Station"
    private const val ORIGIN = "Origin"
    private val pins = WeakHashMap<ServerSubLevel, FixedConstraintHandle>()
    private val dormant = java.util.Collections.newSetFromMap(WeakHashMap<ServerSubLevel, Boolean>())
    private val assembling = ThreadLocal<Map<BlockPos, Set<BlockPos>>>()

    fun construction(sub: ServerSubLevel) = sub.userDataTag?.contains(KEY) == true
    fun constructing() = assembling.get() != null
    private fun data(sub: ServerSubLevel) = checkNotNull(sub.userDataTag).getCompound(KEY)
    fun origin(sub: ServerSubLevel): BlockPos = BlockPos.of(data(sub).getLong(ORIGIN))
    fun toPlot(sub: ServerSubLevel, world: BlockPos): BlockPos = world.subtract(origin(sub)).offset(sub.plot.centerBlock)
    fun toWorld(sub: ServerSubLevel, plot: BlockPos): BlockPos = plot.subtract(sub.plot.centerBlock).offset(origin(sub))

    fun bodies(station: StationBlockEntity): List<ServerSubLevel> {
        val level = station.level as? ServerLevel ?: return emptyList()
        return SubLevelContainer.getContainer(level)?.allSubLevels.orEmpty().filter {
            !it.isRemoved && construction(it) && data(it).getLong(STATION) == station.blockPos.asLong()
        }
    }

    fun begin(station: StationBlockEntity) {
        if (!ServerConfig.hybridTrains() || station.level !is ServerLevel || bodies(station).isNotEmpty()) return
        val direction = station.assemblyDirection ?: return
        create(station, station.edgePoint.globalPosition.relative(direction).above())
    }

    private fun create(station: StationBlockEntity, worldOrigin: BlockPos): ServerSubLevel {
        val level = station.level as ServerLevel
        val pose = Pose3d().also { it.position().set(worldOrigin.x + .5, worldOrigin.y + .5, worldOrigin.z + .5) }
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val sub = container.allocateNewSubLevel(pose) as ServerSubLevel
        sub.userDataTag = CompoundTag().also { tag -> tag.put(KEY, CompoundTag().also {
            it.putLong(STATION, station.blockPos.asLong()); it.putLong(ORIGIN, worldOrigin.asLong())
        }) }
        sub.plot.newEmptyChunk(sub.plot.centerChunk)
        val center = sub.plot.centerBlock
        sub.logicalPose().rotationPoint().set(center.x + .5, center.y + .5, center.z + .5)
        preserveBounds(sub)
        sub.updateBoundingBox()
        sub.updateLastPose()
        park(sub)
        return sub
    }

    private fun bogeys(sub: ServerSubLevel) = sub.plot.loadedChunks.flatMap { holder ->
        holder.chunk.blockEntities.values.filter { it.blockState.block is AbstractBogeyBlock<*> }.map { it.blockPos }
    }

    fun containing(station: StationBlockEntity, world: BlockPos): ServerSubLevel? = bodies(station).firstOrNull {
        val mapped = toPlot(it, world)
        it.plot.contains(mapped.x.toDouble(), mapped.z.toDouble()) &&
            it.plot.getChunkHolder(it.plot.toLocal(net.minecraft.world.level.ChunkPos(mapped))) != null && !it.level.getBlockState(mapped).isAir
    }

    fun state(station: StationBlockEntity, level: Level, pos: BlockPos): BlockState =
        containing(station, pos)?.let { level.getBlockState(toPlot(it, pos)) } ?: level.getBlockState(pos)

    fun blockEntity(station: StationBlockEntity, level: Level, pos: BlockPos) =
        level.getBlockEntity(containing(station, pos)?.let { toPlot(it, pos) } ?: pos)

    fun place(station: StationBlockEntity, level: Level, pos: BlockPos, state: BlockState, flags: Int): Boolean {
        if (level !is ServerLevel || !station.isAssembling ||
            (!ServerConfig.hybridTrains() && bodies(station).isEmpty())) return level.setBlock(pos, state, flags)
        val existing = containing(station, pos)
        val body = existing ?: bodies(station).filter { bogeys(it).size < 2 }
            .minByOrNull { origin(it).distSqr(pos) } ?: create(station, pos)
        val target = toPlot(body, pos)
        val chunkPos = net.minecraft.world.level.ChunkPos(target)
        if (body.plot.getChunkHolder(body.plot.toLocal(chunkPos)) == null) body.plot.newEmptyChunk(chunkPos)
        return level.setBlock(target, state, flags).also { if (it) hold(body) }
    }

    fun destroy(station: StationBlockEntity, level: Level, pos: BlockPos, drops: Boolean): Boolean =
        level.destroyBlock(containing(station, pos)?.let { toPlot(it, pos) } ?: pos, drops)

    /** No connectivity flood fill: disconnected bogeys still belong to this wagon. */
    fun hold(sub: ServerSubLevel) {
        if (!construction(sub) || sub.isRemoved) return
        if (sub.selfMassTracker.isInvalid) { park(sub); return }
        if (pins[sub]?.isValid == true) return
        val container = checkNotNull(SubLevelContainer.getContainer(sub.level))
        wake(sub)
        val anchor = sub.plot.centerBlock
        val world = origin(sub)
        val handle = container.physicsSystem().pipeline.addConstraint(sub, null, FixedConstraintConfiguration(
            Vector3d(anchor.x + .5, anchor.y + .5, anchor.z + .5),
            Vector3d(world.x + .5, world.y + .5, world.z + .5), Quaterniond()))
        if (handle != null) pins[sub] = handle
    }

    fun dormant(sub: ServerSubLevel) = sub in dormant

    /** Empty plots have no physics body. Mass bookkeeping still runs, but its
     * native uploads resume only after the first real center of mass exists. */
    fun park(sub: ServerSubLevel) {
        if (!dormant.add(sub)) return
        pins.remove(sub)?.let { if (it.isValid) it.remove() }
        SubLevelContainer.getContainer(sub.level)?.physicsSystem()?.pipeline?.remove(sub)
    }

    fun wake(sub: ServerSubLevel) {
        if (!dormant(sub)) return
        if (sub.massTracker.centerOfMass == null) return
        updatePose(sub)
        // Keep uploadData suppressed until add() has created the native body.
        SubLevelContainer.getContainer(sub.level)?.physicsSystem()?.pipeline?.add(sub, sub.logicalPose())
        dormant.remove(sub)
        sub.updateLastPose()
    }

    /** Construction has an exact, stationary world-to-plot transform. It must
     * not read a partially initialized native pose while mass data is changing. */
    fun updatePose(sub: ServerSubLevel) {
        val anchor = sub.plot.centerBlock
        val world = origin(sub)
        val center = sub.massTracker.centerOfMass ?: Vector3d(anchor.x + .5, anchor.y + .5, anchor.z + .5)
        sub.logicalPose().orientation().identity()
        sub.logicalPose().rotationPoint().set(center)
        sub.logicalPose().position().set(world.x + center.x() - anchor.x, world.y + center.y() - anchor.y, world.z + center.z() - anchor.z)
    }

    fun statsChanged(pipeline: dev.ryanhcode.sable.api.physics.PhysicsPipeline, sub: ServerSubLevel, notify: () -> Unit) {
        if (dormant(sub)) return
        notify()
        if (construction(sub)) {
            updatePose(sub)
            pipeline.teleport(sub, sub.logicalPose().position(), sub.logicalPose().orientation())
        }
    }

    /** Sable's EMPTY sentinel has infinite inverted bounds. An empty station
     * needs a finite anchor box until its first bogey; it contains no fake block. */
    fun preserveBounds(sub: ServerSubLevel) {
        if (construction(sub) && sub.plot.boundingBox === BoundingBox3i.EMPTY) {
            val center = sub.plot.centerBlock
            sub.plot.setBoundingBox(BoundingBox3i(center.x, center.y, center.z, center.x, center.y, center.z))
        }
    }

    fun blocks(sub: ServerSubLevel): Set<BlockPos> = buildSet {
        for (holder in sub.plot.loadedChunks) {
            val chunk = holder.chunk
            for ((index, section) in chunk.sections.withIndex()) {
                if (section.hasOnlyAir()) continue
                val y0 = chunk.getSectionYFromSectionIndex(index) shl 4
                for (y in 0..15) for (z in 0..15) for (x in 0..15) {
                    if (!section.getBlockState(x, y, z).isAir) add(BlockPos(chunk.pos.minBlockX + x, y0 + y, chunk.pos.minBlockZ + z))
                }
            }
        }
    }

    fun assemblyBlocks(anchor: BlockPos): Set<BlockPos>? = assembling.get()?.get(anchor)

    private fun move(sub: ServerSubLevel, positions: Set<BlockPos>, intoWorld: Boolean) {
        if (positions.isEmpty()) return
        val from = if (intoWorld) sub.plot.centerBlock else origin(sub)
        val to = if (intoWorld) origin(sub) else sub.plot.centerBlock
        val level = sub.level
        val saved = positions.associateWith { pos -> level.getBlockState(pos) to level.getBlockEntity(pos)?.saveWithFullMetadata(level.registryAccess()) }
        try {
            SubLevelAssemblyHelper.moveBlocks(level, AssemblyTransform(from, to, 0, Rotation.NONE, level), positions)
            check(saved.all { (source, entry) ->
                val target = source.subtract(from).offset(to)
                level.getBlockState(source).isAir && level.getBlockState(target) == entry.first &&
                    (entry.second == null || level.getBlockEntity(target) != null)
            }) {
                "Could not move station build ${sub.uniqueId}"
            }
        } catch (failure: RuntimeException) {
            // Sable logs individual listener failures and continues. Never allow
            // that best-effort helper to silently consume construction cargo.
            for (source in positions) {
                val target = source.subtract(from).offset(to)
                level.removeBlockEntity(target)
                level.setBlock(target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18)
            }
            for ((source, entry) in saved) {
                level.removeBlockEntity(source)
                level.setBlock(source, entry.first, 18)
                entry.second?.let { tag -> level.getBlockEntity(source)?.loadWithComponents(tag, level.registryAccess()) }
            }
            throw failure
        }
    }

    /** Preflight every destination before moving any wagon or inventory. */
    fun <T> assemble(station: StationBlockEntity, action: () -> T): T {
        val bodies = bodies(station)
        if (bodies.isEmpty()) return action()
        for (sub in bodies) check(de.TeutonStudio.TeutonLib.integration.train.TrainPortalMachines.prepare(sub)) {
            "Stop the train's moving machinery before assembly"
        }
        val positions = bodies.associateWith(::blocks)
        val worldPositions = positions.mapValues { (sub, blocks) -> blocks.map { toWorld(sub, it) }.toSet() }
        val level = station.level as ServerLevel
        check(worldPositions.values.flatten().let { it.size == it.toSet().size }) { "Station build plots overlap" }
        check(worldPositions.values.flatten().all { level.hasChunkAt(it) && level.getBlockState(it).isAir }) {
            "Clear the blocks occupying the train construction area first"
        }
        val previous = assembling.get()
        val staged = mutableListOf<ServerSubLevel>()
        try {
            for (sub in bodies) {
                pins.remove(sub)?.remove()
                move(sub, positions.getValue(sub), true)
                staged += sub
            }
            assembling.set(buildMap {
                for ((sub, blocks) in positions) for (bogey in blocks.filter { level.getBlockState(toWorld(sub, it)).block is AbstractBogeyBlock<*> }) {
                    put(toWorld(sub, bogey), worldPositions.getValue(sub))
                }
            })
            return action()
        } finally {
            if (previous == null) assembling.remove() else assembling.set(previous)
            for (sub in staged) {
                val remaining = worldPositions.getValue(sub).filter { !level.getBlockState(it).isAir }.toSet()
                move(sub, remaining, false)
                hold(sub)
            }
        }
    }

    /** Leaving construction keeps every placed block in the world. */
    fun finish(station: StationBlockEntity) {
        val bodies = bodies(station)
        val positions = bodies.associateWith(::blocks)
        val level = station.level as? ServerLevel ?: return
        val destinations = positions.flatMap { (sub, blocks) -> blocks.map { toWorld(sub, it) } }
        check(destinations.size == destinations.toSet().size) { "Separate overlapping train construction plots first" }
        check(positions.all { (sub, blocks) -> blocks.all { level.hasChunkAt(toWorld(sub, it)) && level.getBlockState(toWorld(sub, it)).isAir } }) {
            "Clear the train construction area before leaving assembly mode"
        }
        for ((sub, blocks) in positions) {
            pins.remove(sub)?.remove()
            move(sub, blocks, true)
            sub.userDataTag = sub.userDataTag?.copy()?.also { it.remove(KEY) }
            sub.markRemoved()
        }
    }

    fun removed(station: StationBlockEntity) {
        try { finish(station) }
        catch (_: IllegalStateException) {
            // If someone occupied the world space while building, retain the
            // actual construction as ordinary Sable bodies instead of deleting it.
            for (sub in bodies(station)) {
                pins.remove(sub)?.remove()
                sub.userDataTag = sub.userDataTag?.copy()?.also { it.remove(KEY) }
            }
        }
    }
}
