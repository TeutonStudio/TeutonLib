package de.TeutonStudio.TeutonLib.integration.hose

import com.simibubi.create.content.fluids.FluidPropagator
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.entity.BlockEntity

/** The only non-local graph edge: pulley DOWN <-> socket UP, in storage coordinates. */
object HosePipeGraph {
    fun loaded(world: BlockGetter?, pos: BlockPos): BlockEntity? {
        if (world is ServerLevel) return world.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4)?.blockEntities?.get(pos)
        return world?.getBlockEntity(pos)
    }

    fun connector(world: BlockGetter?, pos: BlockPos): HoseDrainConnection? {
        val block = loaded(world, pos)
        HoseDrainConnection.get(block)?.let { return it.takeIf { it.active() } }
        val host = block as? HosePulleyHost ?: return null
        val head = (world as? ServerLevel)?.getEntity(host.physicalHeadId ?: return null) as? HoseHeadEntity ?: return null
        return head.connector()?.takeIf { it.active() }
    }

    fun isVirtual(world: BlockGetter?, pos: BlockPos, side: Direction): Boolean = when (loaded(world, pos)) {
        is com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity -> side == Direction.UP &&
            HoseDrainConnection.get(loaded(world, pos))?.headId != null
        is HosePulleyBlockEntity -> side == Direction.DOWN
        else -> false
    }

    fun physicalEntryBlocked(world: BlockGetter?, pos: BlockPos, side: Direction): Boolean =
        !isVirtual(world, pos, side) && isVirtual(world, pos.relative(side), side.opposite)

    fun remote(world: BlockGetter?, pos: BlockPos, side: Direction): BlockPos? {
        if (!isVirtual(world, pos, side)) return null
        val connector = connector(world, pos) ?: return null
        return if (side == Direction.DOWN) connector.blockPos else connector.head()?.ownerPosition
    }

    fun nativeRate(connector: HoseDrainConnection): Int {
        val world = connector.level ?: return 0
        val head = connector.head() ?: return 0
        val pipe = FluidPropagator.getPipe(world, head.ownerPosition) ?: return 0
        val connection = pipe.getConnection(Direction.DOWN) ?: return 0
        val flow = pipe.getFlow(Direction.DOWN) ?: return 0
        val pressure = connection.pressure.get(flow.inbound)
        return if (pressure <= 0f) 0 else maxOf(1, (pressure / 2f).toInt())
    }
}
