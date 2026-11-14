package de.TeutonStudio.TeutonLib.integration.hose

import com.simibubi.create.content.fluids.FluidPropagator
import com.simibubi.create.content.fluids.FluidTransportBehaviour
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import net.minecraft.world.level.block.entity.BlockEntity
import de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/** Hose state lives in the native Item Drain's behaviour/NBT lifecycle. */
class HoseDrainConnection(val drain: ItemDrainBlockEntity) : FluidTransportBehaviour(drain) {
    val level get() = drain.level
    val blockPos get() = drain.blockPos
    val blockState get() = drain.blockState
    val isRemoved get() = drain.isRemoved
    private fun setChanged() = drain.setChanged()
    private fun sendData() = drain.sendData()
    var identity: UUID = UUID.randomUUID()
        private set
    var headId: UUID? = null
        private set
    var tension = 0.0
    var overloadSeconds = 0.0
    val budget = HoseFlowBudget()
    private var connected = false
    private var displayedTension = -1

    override fun initialize() {
        super.initialize()
        HoseConnectorRegistry.register(this)
    }

    override fun canHaveFlowToward(state: BlockState, direction: Direction): Boolean {
        val world = level ?: return false
        if (world.isClientSide) {
            if (headId == null) return false
        } else if (!active()) return false
        if (direction == Direction.UP) return true
        val adjacent = blockPos.relative(direction)
        val neighbour = HosePipeGraph.loaded(world, adjacent)
        if (get(neighbour)?.headId != null) return false
        return FluidPropagator.hasFluidCapability(world, adjacent, direction.opposite) ||
            FluidPropagator.getPipe(world, adjacent)?.canHaveFlowToward(world.getBlockState(adjacent), direction.opposite) == true
    }

    override fun onNeighborChanged(neighbour: BlockPos) {
        super.onNeighborChanged(neighbour)
        val world = level as? ServerLevel ?: return
        if (headId != null) FluidPropagator.propagateChangedPipe(world, blockPos, blockState)
    }

    override fun destroy() {
        if (level?.isClientSide == false) release()
        super.destroy()
    }

    fun socketLocal(): Vec3 = Vec3(blockPos.x + .5, blockPos.y + 7.0 / 16.0, blockPos.z + .5)
    fun socketWorld(): Vec3 = SubLevelGeometry.projectToWorld(level!!, socketLocal())
    fun head(): HoseHeadEntity? {
        val id = headId ?: return null
        return (level as? ServerLevel)?.getEntity(id) as? HoseHeadEntity
    }
    fun active(): Boolean {
        val head = head() ?: return false
        return HoseConnectorRegistry.find(level!!, identity, head.uuid) === this && !isRemoved && HosePipeGraph.loaded(level, blockPos) === drain && head.isAlive && head.dockId == identity &&
            head.dockPosition == blockPos && head.validOwner() != null && de.TeutonStudio.TeutonLib.integration.sable.HoseCablePhysics.ready(head)
    }

    fun dock(head: HoseHeadEntity, player: Player): Boolean {
        if (headId != null || head.dockId != null || !head.isCarriedBy(player) || head.validOwner() == null) return false
        val target = SubLevelGeometry.projectToWorld(level!!, socketLocal().add(0.0, 5.0/16.0 + HoseCableMath.DOCK_STUB, 0.0))
        if (de.TeutonStudio.TeutonLib.integration.sable.HoseCablePhysics.constrain(head,target).distanceToSqr(target) > 1e-6) return false
        headId = head.uuid
        head.attach(this)
        connected = true
        networkChanged()
        setChanged()
        sendData()
        return true
    }

    fun release() {
        val old = head()
        headId = null
        connected = false
        tension = 0.0
        overloadSeconds = 0.0
        old?.takeIf { it.dockId == identity }?.detach()
        networkChanged(old?.ownerPosition)
        setChanged()
        sendData()
    }

    fun networkChanged(owner: BlockPos? = head()?.ownerPosition) {
        val world = level as? ServerLevel ?: return
        world.invalidateCapabilities(blockPos)
        if (owner != null && HosePipeGraph.loaded(world, owner) != null) {
            world.invalidateCapabilities(owner)
            FluidPropagator.propagateChangedPipe(world, owner, world.getBlockState(owner))
        }
        FluidPropagator.propagateChangedPipe(world, blockPos, blockState)
        for (side in Direction.entries) {
            val adjacent = blockPos.relative(side)
            if (HosePipeGraph.loaded(world, adjacent) != null) {
                FluidPropagator.propagateChangedPipe(world, adjacent, world.getBlockState(adjacent))
            }
        }
        world.updateNeighborsAt(blockPos, blockState.block)
    }

    override fun tick() {
        val world = level as? ServerLevel
        if (world != null) {
            val head = head()
            if (head != null && (!head.isAlive || head.dockId != identity || head.dockPosition != blockPos)) release()
            val display = (tension / de.TeutonStudio.TeutonLib.ServerConfig.hoseBreakForce() * 100).toInt()
            if (world.gameTime % 5L == 0L && display != displayedTension) {
                displayedTension = display
                sendData()
            }
            val now = active()
            if (now != connected) {
                connected = now
                networkChanged()
            }
        }
        if (active()) super.tick()
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        val saved = CompoundTag()
        saved.putUUID("ConnectorId", identity)
        headId?.let { saved.putUUID("HeadId", it) }
        saved.putDouble("Tension", tension)
        tag.put("TeutonLibHose", saved)
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        val wasLocked = headId != null
        val saved = tag.getCompound("TeutonLibHose")
        if (saved.hasUUID("ConnectorId")) identity = saved.getUUID("ConnectorId")
        headId = if (saved.hasUUID("HeadId")) saved.getUUID("HeadId") else null
        tension = saved.getDouble("Tension")
        overloadSeconds = 0.0
        if (clientPacket && wasLocked != (headId != null)) {
            drain.requestModelDataUpdate()
            level?.sendBlockUpdated(blockPos, blockState, blockState, 8)
        }
    }

    fun addToGoggleTooltip(tooltip: MutableList<Component>): Boolean {
        tooltip.add(Component.translatable(if (headId == null) "teutonlib.hose_connector.empty" else "teutonlib.hose_connector.connected"))
        tooltip.add(Component.translatable("teutonlib.hose_connector.flow"))
        tooltip.add(Component.translatable("teutonlib.hose_connector.tension", (tension / de.TeutonStudio.TeutonLib.ServerConfig.hoseBreakForce() * 100).toInt()))
        return true
    }
    companion object {
        @JvmStatic fun get(blockEntity: BlockEntity?): HoseDrainConnection? =
            if (blockEntity is ItemDrainBlockEntity) BlockEntityBehaviour.get(blockEntity, TYPE) as? HoseDrainConnection else null
    }
}
