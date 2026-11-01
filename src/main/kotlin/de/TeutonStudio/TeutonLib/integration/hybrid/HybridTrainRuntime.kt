package de.TeutonStudio.TeutonLib.integration.hybrid

import com.simibubi.create.content.contraptions.Contraption
import com.simibubi.create.content.contraptions.StructureTransform
import com.simibubi.create.content.trains.entity.CarriageContraption
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import com.simibubi.create.content.trains.entity.Train
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers
import de.TeutonStudio.TeutonLib.integration.TrainPortalCompatibility
import de.TeutonStudio.TeutonLib.integration.train.CtfPortalAccess
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/** One live plot owns inventories, block entities, scheduled ticks and nested contraptions.
 * A mode change never copies that data or reconstructs it from Create's block snapshot. */
object HybridTrainRuntime {
    const val MODE_KEY = "teutonlib:train_mode"
    const val PARENT_KEY = "teutonlib:train_parent"
    enum class Mode { DRIVING, EDITING }
    private data class WriteScope(val level: Level, val positions: Set<BlockPos>)
    private val writes = ThreadLocal<List<WriteScope>>()
    private val automatic = ThreadLocal<Boolean>()
    private val maintenance = ThreadLocal<Set<java.util.UUID>>()

    fun <T> maintain(sub: ServerSubLevel, action: () -> T): T {
        val previous = maintenance.get()
        maintenance.set(previous.orEmpty() + sub.uniqueId)
        return try { action() } finally { if (previous == null) maintenance.remove() else maintenance.set(previous) }
    }

    fun <T> automatically(action: () -> T): T {
        val previous = automatic.get()
        automatic.set(true)
        return try { action() } finally { if (previous == null) automatic.remove() else automatic.set(previous) }
    }

    fun sub(level: ServerLevel, link: SubLevelLinks.Link): ServerSubLevel? =
        level.server.getLevel(link.dimension)?.let { SubLevelContainer.getContainer(it)?.getSubLevel(link.subLevelId) as? ServerSubLevel }

    fun mode(sub: ServerSubLevel): Mode = when (val value = sub.userDataTag?.getString(MODE_KEY)) {
        null, "", Mode.DRIVING.name -> Mode.DRIVING
        Mode.EDITING.name -> Mode.EDITING
        else -> error("Unknown hybrid train mode '$value' for ${sub.uniqueId}")
    }

    fun setMode(sub: ServerSubLevel, mode: Mode) {
        check(sub.level.server.isSameThread)
        val data = sub.userDataTag?.copy() ?: CompoundTag()
        data.putString(MODE_KEY, mode.name)
        sub.userDataTag = data
    }

    /** Explicit CTF conversion means editing; automatic conversion means normal operation. */
    fun converted(level: ServerLevel, entity: com.simibubi.create.content.contraptions.AbstractContraptionEntity,
        link: SubLevelLinks.Link?) {
        if (link == null || !ServerConfig.hybridTrains()) return
        val body = sub(level, link) ?: return
        if (automatic.get() == true) {
            if (body.userDataTag?.contains(MODE_KEY) != true) setMode(body, Mode.DRIVING)
        } else if ((!TrainPortalCompatibility.active() || !TrainPortalTransfers.manages(link)) &&
            entity is CarriageContraptionEntity && entity.carriage?.train?.let {
            kotlin.math.abs(it.speed) < 1e-6 && !it.carriages.any { c -> c.presentInMultipleDimensions() }
        } == true) setMode(body, Mode.EDITING)
    }

    fun editing(train: Train): Boolean = ServerConfig.hybridTrains() && SubLevelLinks.links().any { link ->
            CtfPortalAccess.carriage(link)?.train === train &&
            train.carriages.firstNotNullOfOrNull { it.anyAvailableEntity()?.level() as? ServerLevel }?.let { sub(it, link) }
                ?.let { mode(it) == Mode.EDITING } == true
    }

    /** Preflight the entire train before changing any carriage. */
    fun changeMode(level: ServerLevel, train: Train, mode: Mode): Int {
        check(level.server.isSameThread)
        check(kotlin.math.abs(train.speed) < 1e-6) { "Stop the train before changing its mode" }
        check(train.carriages.none { it.presentInMultipleDimensions() }) { "Finish the portal crossing first" }
        val bodies = train.carriages.map { carriage ->
            val link = checkNotNull(SubLevelLinks.links().firstOrNull { CtfPortalAccess.carriage(it) === carriage }) {
                "Wait until every carriage's simulation has loaded"
            }
            check(!TrainPortalCompatibility.active() || !TrainPortalTransfers.manages(link)) { "Finish the portal crossing first" }
            checkNotNull(sub(level, link)) { "A carriage's simulation has not loaded" }.also { check(!it.isRemoved) }
        }
        train.speed = 0.0
        train.targetSpeed = 0.0
        bodies.forEach { setMode(it, mode) }
        return bodies.size
    }

    private fun protectedSub(level: Level, pos: BlockPos): ServerSubLevel? {
        if (level !is ServerLevel || !ServerConfig.hybridTrains()) return null
        val body = Sable.HELPER.getContaining(level, pos) as? ServerSubLevel ?: return null
        return body.takeIf(::locked)
    }

    fun owningRoot(body: ServerSubLevel): ServerSubLevel? {
        var current = body
        val visited = mutableSetOf<java.util.UUID>()
        while (visited.add(current.uniqueId)) {
            if (SubLevelLinks.linkForSubLevel(current.uniqueId) != null || SubLevelLinks.isOursEvenIfOrphaned(current.level, current.uniqueId)) return current
            val data = current.userDataTag ?: return null
            if (!data.hasUUID(PARENT_KEY)) return null
            val parent = SubLevelContainer.getContainer(current.level)?.getSubLevel(data.getUUID(PARENT_KEY)) as? ServerSubLevel ?: return null
            val childId = current.uniqueId
            if (parent.plot.blockEntityActors.none { actor ->
                actor.`sable$getConnectionDependencies`()?.any { it.uniqueId == childId } == true
            }) return null
            current = parent
        }
        return null
    }

    fun trainOwned(body: ServerSubLevel): Boolean = owningRoot(body) != null
    fun locked(body: ServerSubLevel): Boolean = ServerConfig.hybridTrains() && !body.isRemoved &&
        owningRoot(body)?.let { mode(it) == Mode.DRIVING } == true

    fun permits(level: Level, pos: BlockPos, before: BlockState, after: BlockState): Boolean {
        if (before.block === after.block) return true
        if (writes.get().orEmpty().any { it.level === level && pos in it.positions }) return true
        val body = protectedSub(level, pos) ?: return true
        // Native transfer cleanup is scoped to exactly this body, including rollback.
        return body.uniqueId in maintenance.get().orEmpty() ||
            (TrainPortalCompatibility.active() && TrainPortalTransfers.transferring(body.uniqueId))
    }

    /** A child may assemble/disassemble its own captured blocks while the parent stays fixed.
     * Scope is position-limited and exception-safe; unrelated machine writes remain blocked. */
    fun <T> childWrite(contraption: Contraption, level: Level, positions: Set<BlockPos>, action: () -> T): T {
        if (level !is ServerLevel || contraption is CarriageContraption || positions.isEmpty()) return action()
        val bodies = positions.mapNotNull { protectedSub(level, it) }.distinctBy { it.uniqueId }
        if (bodies.isEmpty()) return action()
        check(bodies.size == 1 && positions.all { Sable.HELPER.getContaining(level, it) === bodies.single() }) {
            "A child contraption cannot move blocks across a hybrid train boundary"
        }
        val previous = writes.get()
        writes.set(previous.orEmpty() + WriteScope(level, positions))
        return try { action() } finally { if (previous == null) writes.remove() else writes.set(previous) }
    }

    fun removalPositions(contraption: Contraption, offset: BlockPos) =
        contraption.blocks.keys.map { it.offset(contraption.anchor).offset(offset).immutable() }.toSet()
    fun placementPositions(contraption: Contraption, transform: StructureTransform) =
        contraption.blocks.keys.map { transform.apply(it).immutable() }.toSet()
}
