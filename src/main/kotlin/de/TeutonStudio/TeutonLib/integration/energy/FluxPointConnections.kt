package de.TeutonStudio.TeutonLib.integration.energy

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/** Keep the saved property names; they now select a fitted extension arm on every input face. */
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
            state.getValue(connected.getValue(side)) && NewAgeMotorEnergy.isMotorExtensionConnection(level, pos, side))
        return next
    }

    fun reconcile(level: Level, pos: BlockPos) {
        val state = level.getBlockState(pos)
        val next = update(state, level, pos)
        if (next !== state) level.setBlock(pos, next, Block.UPDATE_CLIENTS)
    }

    fun facings(state: BlockState, level: BlockGetter?, pos: BlockPos): List<Direction?> = Direction.entries.map { side ->
        if (state.getValue(connected.getValue(side)) && state.getValue(REAR.getValue(side)))
            if (level is Level && !level.hasChunkAt(pos.relative(side))) side
            else level?.let { NewAgeMotorEnergy.extensionFacing(it.getBlockState(pos.relative(side))) } ?: side
        else null
    }
    fun specialized(state: BlockState, side: Direction) = state.getValue(connected.getValue(side)) && state.getValue(REAR.getValue(side))
    private val shapes = java.util.concurrent.ConcurrentHashMap<Int,VoxelShape>()
    fun shape(state: BlockState, level: BlockGetter? = null, pos: BlockPos = BlockPos.ZERO): VoxelShape {
        val facings=facings(state,level,pos)
        var key=0
        for (side in Direction.entries) key=key*8+if (!state.getValue(connected.getValue(side))) 0
            else facings[side.ordinal]?.let { it.ordinal+2 } ?: 1
        return shapes.getOrPut(key) { buildShape(state,facings) }
    }
    private fun buildShape(state: BlockState, facings: List<Direction?>): VoxelShape {
        var result = Block.box(5.5, 5.5, 5.5, 10.5, 10.5, 10.5)
        for (side in Direction.entries) {
            if (!state.getValue(connected.getValue(side))) continue
            for (box in MotorExtensionConnectionGeometry.arm(side,facings[side.ordinal]))
                result=Shapes.or(result,Block.box(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ))
        }
        return result
    }
}
