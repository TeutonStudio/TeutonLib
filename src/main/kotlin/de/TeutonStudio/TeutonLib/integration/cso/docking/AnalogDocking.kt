package de.TeutonStudio.TeutonLib.integration.cso.docking

import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.cso.bridge.GearBridgeManager
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair
import dev.simulated_team.simulated.index.SimBlocks
import dev.simulated_team.simulated.index.SimSoundEvents
import dev.simulated_team.simulated.util.SimMovementContext
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.state.properties.BlockStateProperties as Props
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d

/** Only reached from Mixins selected by CsoAvailability for the pinned mod versions. */
object AnalogDocking {
    fun configured() = ServerConfig.SPEC.isLoaded && ServerConfig.ENABLE_ANALOG_DOCKING.asBoolean
    fun access(dock: DockingConnectorBlockEntity) = dock as AnalogDockingAccess
    fun active(dock: DockingConnectorBlockEntity): Boolean =
        if (dock.level?.isClientSide == true) access(dock).analogActive else configured()

    fun requested(dock: DockingConnectorBlockEntity): DockingLength =
        DockingLength.fromSignal(dock.level!!.getBestNeighborSignal(dock.blockPos))

    private fun facing(dock: DockingConnectorBlockEntity) = dock.blockState.getValue(Props.FACING)
    private fun ownsFront(dock: DockingConnectorBlockEntity): Boolean {
        val state = dock.level!!.getBlockState(dock.blockPos.relative(facing(dock)))
        return state.`is`(SimBlocks.PAIRED_DOCKING_CONNECTOR.get()) && state.getValue(Props.FACING) == facing(dock).opposite
    }
    private fun clearFront(dock: DockingConnectorBlockEntity) {
        if (ownsFront(dock)) dock.level!!.removeBlock(dock.blockPos.relative(facing(dock)), false)
    }
    private fun clearPath(dock: DockingConnectorBlockEntity): Boolean =
        dock.level!!.getBlockState(dock.blockPos.relative(facing(dock))).isAir || ownsFront(dock)

    private fun worldPoint(dock: DockingConnectorBlockEntity, point: Vec3): Vec3 =
        dock.latestSubLevel?.logicalPose()?.transformPosition(point) ?: point

    private fun worldFacing(dock: DockingConnectorBlockEntity): Vec3 {
        val local = Vector3d(facing(dock).stepX.toDouble(), facing(dock).stepY.toDouble(), facing(dock).stepZ.toDouble())
        dock.latestSubLevel?.logicalPose()?.orientation()?.transform(local)
        return Vec3(local.x, local.y, local.z)
    }

    /** Prospective geometry uses requested lengths, never a previous negotiated length. */
    fun candidateDistance(a: DockingConnectorBlockEntity, b: DockingConnectorBlockEntity): Double? {
        if (a === b || a.level !== b.level || a.isRemoved || b.isRemoved) return null
        val length = requested(a).negotiate(requested(b))
        if (length == DockingLength.OFF) return null
        // A FULL connector may already own the shared middle cell before its HALF
        // partner loads/approaches. Negotiate before treating that arm as an obstacle.
        val sharedHalfCell = length == DockingLength.HALF &&
            a.blockPos.relative(facing(a)) == b.blockPos.relative(facing(b))
        if (!clearPath(a) && !(sharedHalfCell && ownsFront(b))) return null
        if (!clearPath(b) && !(sharedHalfCell && ownsFront(a))) return null
        if (b.otherConnectorPosition != null && b.otherConnectorPosition != a.blockPos) return null
        if (a.latestSubLevel === b.latestSubLevel) {
            if (facing(a) != facing(b).opposite || b.blockPos != a.blockPos.relative(facing(a), length.baseDistance)) return null
        }
        val normalA = worldFacing(a)
        val normalB = worldFacing(b)
        if (normalA.dot(normalB) >= 0) return null
        val baseA = worldPoint(a, a.blockPos.center)
        val baseB = worldPoint(b, b.blockPos.center)
        val delta = baseB.subtract(baseA)
        if (delta.dot(normalA) <= 0 || delta.dot(normalB) >= 0) return null
        val tipA = baseA.add(normalA.scale(length.contactOffset))
        val tipB = baseB.add(normalB.scale(length.contactOffset))
        return tipA.distanceToSqr(tipB).takeIf { it < 16 }
    }

    /** Bounded search; two independent selections are compared, with no recursive search. */
    fun select(dock: DockingConnectorBlockEntity): DockingConnectorBlockEntity? {
        val level = dock.level ?: return null
        if (requested(dock) == DockingLength.OFF) return null
        dock.otherConnector?.let { if (candidateDistance(dock, it) != null) return it }
        val positions = linkedSetOf<BlockPos>()
        dock.otherConnectorPosition?.let(positions::add)
        for (distance in 2..3) positions.add(dock.blockPos.relative(facing(dock), distance))
        val context = SimMovementContext.getMovementContext(level, dock.blockPos.center)
        DockingConnectorBlockEntity.MAGNET_CONTROLLER.findNearby(context).forEach { positions.add(it.localBlockPos()) }
        return DockingSelection.choose(positions.asSequence().filter { level.hasChunkAt(it) }
            .mapNotNull { level.getBlockEntity(it) as? DockingConnectorBlockEntity }
            .mapNotNull { other -> candidateDistance(dock, other)?.let { DockingSelection.Candidate(other, it, other.blockPos.asLong()) } }
        )
    }

    fun detach(dock: DockingConnectorBlockEntity) {
        val other = dock.otherConnector
        // Upstream unDock removes the constraint, fluid/FE and CC connections. The existing
        // DockingConnectorMixin removes TeutonLib's cogwheel edges at its HEAD.
        dock.unDock()
        dock.inventory.unDock()
        if (other?.otherConnectorPosition == dock.blockPos) {
            other.unDock()
            other.inventory.unDock()
        }
        dock.feet.setValue(0.0)
        other?.feet?.setValue(0.0)
    }

    private fun apply(dock: DockingConnectorBlockEntity, length: DockingLength, partner: BlockPos?) {
        val state = access(dock)
        val request = requested(dock)
        val previousPower = dock.powered
        val changed = state.requestedLength != request || state.effectiveLength != length || state.selectedPartner != partner || !state.analogActive
        if (state.effectiveLength != length || (dock.otherConnectorPosition != null && dock.otherConnectorPosition != partner)) detach(dock)
        state.analogActive = true
        state.requestedLength = request
        state.effectiveLength = length
        state.selectedPartner = partner
        if (length != DockingLength.FULL) clearFront(dock)
        dock.powered = length != DockingLength.OFF && clearPath(dock)
        if (changed || previousPower != dock.powered) { dock.setChanged(); dock.sendData() }
    }

    fun tick(dock: DockingConnectorBlockEntity) {
        val level = dock.level ?: return
        val state = access(dock)
        if (!level.isClientSide) {
            val selected = select(dock)?.takeIf { select(it) === dock }
            val length = if (selected == null) requested(dock) else requested(dock).negotiate(requested(selected))
            apply(dock, length, selected?.blockPos)
            if (selected != null) apply(selected, length, dock.blockPos)
            if (dock.otherConnectorPosition != null && (selected == null || selected.otherConnectorPosition != dock.blockPos)) detach(dock)
            if (!dock.powered && dock.hasOtherConnector()) detach(dock)
        }
        val target = if (dock.powered) state.effectiveLength.blocks else 0f
        val previousTarget = dock.extension.chaseTarget
        dock.extension.updateChaseTarget(target)
        if (level.isClientSide && target != previousTarget) {
            val sound = if (target > previousTarget) SimSoundEvents.DOCKING_CONNECTOR_EXTENDS else SimSoundEvents.DOCKING_CONNECTOR_RETRACTS
            level.playLocalSound(dock.blockPos.x + .5, dock.blockPos.y + .5, dock.blockPos.z + .5,
                sound.event(), SoundSource.BLOCKS, .75f, 1f, false)
        }
        dock.extension.tickChaser()
        dock.feet.updateChaseTarget(if (dock.hasOtherConnector()) 1f else 0f)
        dock.feet.tickChaser()
        if (!level.isClientSide) {
            if (dock.blockState.getValue(Props.EXTENDED) != dock.isExtended) {
                level.setBlock(dock.blockPos, dock.blockState.setValue(Props.EXTENDED, dock.isExtended), 6)
            }
            if (state.effectiveLength == DockingLength.FULL && dock.isExtended && level.getBlockState(dock.blockPos.relative(facing(dock))).isAir) {
                level.setBlock(dock.blockPos.relative(facing(dock)), SimBlocks.PAIRED_DOCKING_CONNECTOR.get().defaultBlockState().setValue(Props.FACING, facing(dock).opposite), 3)
            }
            state.updateDockingState()
            state.closestDistance = Double.MAX_VALUE
            state.selectedPartner?.let { position ->
                val other = level.getBlockEntity(position) as? DockingConnectorBlockEntity
                if (other != null && dock.isExtended && other.isExtended && pairAllowed(dock, other)) {
                    state.closestDistance = DockingConnectorPair.getRelativePosition(dock, other, Vector3d()).length()
                    DockingConnectorBlockEntity.MAGNET_CONTROLLER.tryAddPair(level, dock.blockPos, position, ::DockingConnectorPair)
                }
            }
            // The analog tick cancels the original body, including its TAIL injection.
            if (de.TeutonStudio.TeutonLib.integration.CsoRuntime.enabled()) GearBridgeManager.tickDock(dock)
        } else if (de.TeutonStudio.TeutonLib.client.CsoClient.enabled()) {
            de.TeutonStudio.TeutonLib.integration.cso.bridge.ClientGearBridgeManager.tickDock(dock)
        }
    }

    /** Also guards stale pairs, reloads and upstream pairTo(force=true). */
    fun pairAllowed(a: DockingConnectorBlockEntity, b: DockingConnectorBlockEntity): Boolean {
        val x = access(a); val y = access(b)
        val negotiated = requested(a).negotiate(requested(b))
        return negotiated != DockingLength.OFF && x.analogActive && y.analogActive &&
            x.effectiveLength == negotiated && y.effectiveLength == negotiated &&
            x.selectedPartner == b.blockPos && y.selectedPartner == a.blockPos &&
            candidateDistance(a, b) != null
    }

    fun restoreOriginal(dock: DockingConnectorBlockEntity) {
        detach(dock)
        clearFront(dock)
        access(dock).analogActive = false
        access(dock).selectedPartner = null
        dock.extension.setValue(0.0)
        dock.sendData()
    }
}
