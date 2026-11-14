package de.TeutonStudio.TeutonLib.integration.energy

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/** Synced state is the single source of truth for both the multipart model and hit box. */
object FluxPointConnections {
    @JvmField val REAR = Direction.entries.associateWith { BooleanProperty.create("teutonlib_rear_${it.serializedName}") }
    private val connected = mapOf(Direction.DOWN to BlockStateProperties.DOWN, Direction.UP to BlockStateProperties.UP,
        Direction.NORTH to BlockStateProperties.NORTH, Direction.SOUTH to BlockStateProperties.SOUTH,
        Direction.WEST to BlockStateProperties.WEST, Direction.EAST to BlockStateProperties.EAST)

    fun isPoint(block: Block) = block.javaClass.name == "sonar.fluxnetworks.common.block.FluxPointBlock"

    fun update(state: BlockState, level: Level, pos: BlockPos): BlockState {
        if (level.isClientSide || !state.hasProperty(REAR.getValue(Direction.DOWN))) return state
        var next = state
        for (side in Direction.entries) next = next.setValue(REAR.getValue(side),
            state.getValue(connected.getValue(side)) && NewAgeMotorEnergy.isRearMotorExtensionConnection(level, pos, side))
        return next
    }

    fun reconcile(level: Level, pos: BlockPos) {
        val state = level.getBlockState(pos)
        val next = update(state, level, pos)
        if (next !== state) level.setBlock(pos, next, Block.UPDATE_CLIENTS)
    }

    private val shapes = java.util.concurrent.ConcurrentHashMap<Int,VoxelShape>()
    fun shape(state: BlockState): VoxelShape {
        // Disconnected rear flags do not alter geometry: 729 distinct shapes instead of 8192.
        var key=0
        for (side in Direction.entries) key=key*3+if (!state.getValue(connected.getValue(side))) 0
            else if (state.getValue(REAR.getValue(side))) 2 else 1
        return shapes.getOrPut(key) { buildShape(state) }
    }
    private fun buildShape(state: BlockState): VoxelShape {
        var result = Block.box(5.5, 5.5, 5.5, 10.5, 10.5, 10.5)
        for (side in Direction.entries) {
            if (!state.getValue(connected.getValue(side))) continue
            val inset = if (state.getValue(REAR.getValue(side))) 2.0 else 0.0
            // Rotate the canonical downward shaft and flange, including the displaced plate.
            fun box(width: Double, start: Double, end: Double): VoxelShape {
                val low = 8.0 - width / 2
                val high = 8.0 + width / 2
                return when (side) {
                    Direction.DOWN -> Block.box(low, start, low, high, end, high)
                    Direction.UP -> Block.box(low, 16-end, low, high, 16-start, high)
                    Direction.NORTH -> Block.box(low, low, start, high, high, end)
                    Direction.SOUTH -> Block.box(low, low, 16-end, high, high, 16-start)
                    Direction.WEST -> Block.box(start, low, low, end, high, high)
                    Direction.EAST -> Block.box(16-end, low, low, 16-start, high, high)
                }
            }
            result = Shapes.or(result, box(2.0, 1-inset, 6.0), box(6.0, -inset, 1-inset))
        }
        return result
    }
}
