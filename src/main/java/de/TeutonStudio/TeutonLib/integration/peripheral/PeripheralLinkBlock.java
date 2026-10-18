package de.TeutonStudio.TeutonLib.integration.peripheral;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PeripheralLinkBlock extends BaseEntityBlock {
    public static final MapCodec<PeripheralLinkBlock> CODEC = simpleCodec(PeripheralLinkBlock::new);
    public PeripheralLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DirectionalBlock.FACING, net.minecraft.core.Direction.UP));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(DirectionalBlock.FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(DirectionalBlock.FACING, context.getClickedFace());
    }
    @Override protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(DirectionalBlock.FACING, rotation.rotate(state.getValue(DirectionalBlock.FACING)));
    }
    @Override protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(DirectionalBlock.FACING)));
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block,
                                             BlockPos neighbour, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PeripheralLinkBlockEntity link
                && neighbour.equals(link.devicePosition())) link.invalidatePeripheral();
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PeripheralLinkBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.DATA_GATHERER.get(state.getValue(DirectionalBlock.FACING));
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PeripheralLinkBlockEntity link)
            link.target(PeripheralLinkItem.selection(stack));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PeripheralLinkBlockEntity link) {
            LinkRegistry.validate();
            player.displayClientMessage(Component.translatable("teutonlib.peripheral_link.status",
                    link.target() == null ? "—" : link.target().toString(),
                    Component.translatable("teutonlib.peripheral_link.status." + link.status)), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
