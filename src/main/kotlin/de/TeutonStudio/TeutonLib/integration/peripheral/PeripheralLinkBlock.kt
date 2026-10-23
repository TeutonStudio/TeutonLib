package de.TeutonStudio.TeutonLib.integration.peripheral

import com.mojang.serialization.MapCodec
import com.simibubi.create.AllShapes
import java.util.function.Function
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class PeripheralLinkBlock(properties: Properties) : BaseEntityBlock(properties) {
    init {
        registerDefaultState(
            defaultBlockState().setValue<Direction, Direction>(
                DirectionalBlock.FACING,
                Direction.UP
            )
        )
    }

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return CODEC
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(DirectionalBlock.FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        return defaultBlockState().setValue<Direction, Direction>(DirectionalBlock.FACING, context.clickedFace)
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        return state.setValue<Direction, Direction>(
            DirectionalBlock.FACING,
            rotation.rotate(state.getValue<Direction>(DirectionalBlock.FACING))
        )
    }

    override fun mirror(state: BlockState, mirror: Mirror): BlockState {
        return rotate(state, mirror.getRotation(state.getValue<Direction>(DirectionalBlock.FACING)))
    }

    override fun neighborChanged(
        state: BlockState, level: Level, pos: BlockPos, block: Block,
        neighbour: BlockPos, moving: Boolean
    ) {
        val link = level.getBlockEntity(pos) as? PeripheralLinkBlockEntity ?: return
        if (!level.isClientSide
            && neighbour == link.devicePosition()
        ) link.invalidatePeripheral()
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return PeripheralLinkBlockEntity(pos, state)
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.MODEL
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return AllShapes.DATA_GATHERER.get(state.getValue<Direction>(DirectionalBlock.FACING))
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        val link = level.getBlockEntity(pos) as? PeripheralLinkBlockEntity ?: return
        if (!level.isClientSide) link.target(
            PeripheralLinkItem.Companion.selection(stack)
        )
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        val link = level.getBlockEntity(pos) as? PeripheralLinkBlockEntity ?: return InteractionResult.sidedSuccess(level.isClientSide)
        if (!level.isClientSide && player is ServerPlayer) player.openMenu(link, pos)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    companion object {
        @JvmField
        val CODEC: MapCodec<PeripheralLinkBlock> =
            simpleCodec<PeripheralLinkBlock>(Function { properties: Properties? -> PeripheralLinkBlock(properties!!) })
    }
}
