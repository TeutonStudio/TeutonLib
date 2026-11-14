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
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.core.registries.Registries
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
    private const val VIEW = "teutonlib:station_build_view"
    private val views = java.util.Collections.synchronizedMap(WeakHashMap<StationBlockEntity, Map<BlockPos, BlockState>>())
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
        val track = station.edgePoint.track ?: return
        val pos = station.edgePoint.globalPosition
        val up = BlockPos.containing(track.getUpNormal(station.level, pos, station.edgePoint.trackBlockState))
        create(station, pos.relative(direction).offset(up))
        sync(station)
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
        if (level.isClientSide) views[station]?.get(pos) ?: level.getBlockState(pos)
        else containing(station, pos)?.let { level.getBlockState(toPlot(it, pos)) } ?: level.getBlockState(pos)

    /** Create recomputes its preview from block states on the client. Only the
     * projected bogey states travel in the station's normal BE update packet;
     * tracks and native offset/spacing validation stay in the real world. */
    private fun projectedBogeys(station: StationBlockEntity): Map<BlockPos, BlockState> = buildMap {
        for (body in bodies(station)) for (pos in bogeys(body))
            put(toWorld(body, pos), body.level.getBlockState(pos))
    }

    fun sync(station: StationBlockEntity) {
        if (station.level !is ServerLevel) return
        val current = projectedBogeys(station)
        if (views[station] == current) return
        views[station] = current
        station.refreshAssemblyInfo()
        station.notifyUpdate()
    }

    fun writeView(station: StationBlockEntity, tag: CompoundTag) {
        val view = projectedBogeys(station)
        if (bodies(station).isEmpty()) return
        tag.put(VIEW, ListTag().also { list -> view.forEach { (pos, state) ->
            list.add(CompoundTag().also {
                it.putLong("Pos", pos.asLong()); it.put("State", NbtUtils.writeBlockState(state))
            })
        } })
    }

    fun readView(station: StationBlockEntity, tag: CompoundTag, registries: net.minecraft.core.HolderLookup.Provider) {
        views[station] = buildMap {
            for (entry in tag.getList(VIEW, 10)) {
                val item = entry as CompoundTag
                put(BlockPos.of(item.getLong("Pos")), NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), item.getCompound("State")))
            }
        }
    }

    fun blockEntity(station: StationBlockEntity, level: Level, pos: BlockPos) =
        level.getBlockEntity(containing(station, pos)?.let { toPlot(it, pos) } ?: pos)

    fun place(station: StationBlockEntity, level: Level, pos: BlockPos, state: BlockState, flags: Int): Boolean {
        if (level !is ServerLevel || !station.isAssembling ||
            (!ServerConfig.hybridTrains() && bodies(station).isEmpty())) return level.setBlock(pos, state, flags)
        val existing = containing(station, pos)
        // Extend only a neighbouring one-bogey wagon in native rail order.
        // Never join across an intervening wagon merely because its origin is nearer.
        val direction = station.assemblyDirection
        fun offset(at: BlockPos): Int = at.subtract(station.edgePoint.globalPosition).let {
            it.x * direction.stepX + it.z * direction.stepZ
        }
        val ordered = bodies(station).flatMap { body -> bogeys(body).map { toWorld(body, it) to body } }
            .sortedBy { offset(it.first) }
        val neighbours = listOfNotNull(ordered.lastOrNull { offset(it.first) < offset(pos) },
            ordered.firstOrNull { offset(it.first) > offset(pos) })
        val body = existing ?: neighbours.filter { bogeys(it.second).size == 1 }
            .minByOrNull { kotlin.math.abs(offset(it.first) - offset(pos)) }?.second
            ?: bodies(station).firstOrNull { blocks(it).isEmpty() } ?: create(station, pos)
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

    private fun checkDestinations(bodies: List<ServerSubLevel>) {
        val destinations = bodies.flatMap { sub -> blocks(sub).map { toWorld(sub, it) } }
        check(destinations.size == destinations.toSet().size) { "Station build plots overlap" }
        check(bodies.all { sub -> blocks(sub).all { pos ->
            val target = toWorld(sub, pos)
            sub.level.hasChunkAt(target) && sub.level.getBlockState(target).isAir
        } }) { "Clear the blocks occupying the train construction area first" }
    }

    /** Preflight every destination before moving any wagon or inventory. */
    fun <T> assemble(station: StationBlockEntity, action: () -> T): T {
        val bodies = bodies(station)
        if (bodies.isEmpty()) return action()
        // Reject an occupied destination before stopping any running machinery.
        checkDestinations(bodies)
        for (sub in bodies) check(de.TeutonStudio.TeutonLib.integration.train.TrainPortalMachines.prepare(sub)) {
            "Stop the train's moving machinery before assembly"
        }
        checkDestinations(bodies) // disassembly may have restored child blocks
        val positions = bodies.associateWith(::blocks)
        val worldPositions = positions.mapValues { (sub, blocks) -> blocks.map { toWorld(sub, it) }.toSet() }
        val level = station.level as ServerLevel
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
        if (bodies.isEmpty()) return
        checkDestinations(bodies)
        for (sub in bodies) check(de.TeutonStudio.TeutonLib.integration.train.TrainPortalMachines.prepare(sub)) {
            "Stop the train's moving machinery before leaving assembly mode"
        }
        checkDestinations(bodies)
        val positions = bodies.associateWith(::blocks)
        val staged = mutableListOf<ServerSubLevel>()
        try {
            for ((sub, blocks) in positions) {
                pins.remove(sub)?.let { if (it.isValid) it.remove() }
                move(sub, blocks, true)
                staged += sub
            }
        } catch (failure: RuntimeException) {
            for (sub in staged.asReversed()) {
                move(sub, positions.getValue(sub).map { toWorld(sub, it) }.toSet(), false)
                hold(sub)
            }
            throw failure
        }
        val level = station.level as ServerLevel
        de.TeutonStudio.TeutonLib.integration.train.TrainPortalMachines.returnedToWorld(level,
            positions.flatMap { (sub, blocks) -> blocks.map { toWorld(sub, it) } })
        // Commit removal only after every wagon has reached its destination.
        for (sub in bodies) {
            // Removal is deferred by Sable. Keep the marker until disposal so
            // an immediate save cannot serialize an unmarked zero-mass body.
            sub.markRemoved()
        }
        sync(station)
    }

    fun removed(station: StationBlockEntity) {
        try { finish(station) }
        catch (_: IllegalStateException) {
            // Keep ownership and the empty-plot persistence invariant when a
            // blocked handoff cannot complete. Never reclassify a dormant plot
            // as an ordinary body whose serializer requires real mass.
            for (sub in bodies(station)) hold(sub)
        }
    }
}
