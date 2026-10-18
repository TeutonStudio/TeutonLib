package de.TeutonStudio.TeutonLib.integration.peripheral;

import java.util.UUID;
import de.TeutonStudio.TeutonLib.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PeripheralLinkItem extends BlockItem {
    public PeripheralLinkItem(Block block, Properties properties) { super(block, properties); }
    static UUID selection(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID("TeutonLibSelectedTarget") ? tag.getUUID("TeutonLibSelectedTarget") : null;
    }
    private static void clear(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove("TeutonLibSelectedTarget"); tag.remove("TeutonLibSelectedDimension");
        });
    }
    private static boolean allowed(Player player, ServerLevel level, BlockPos pos, ItemStack stack) {
        double reach = player.blockInteractionRange() + 1;
        return level.hasChunkAt(pos) && level.mayInteract(player, pos)
                && player.mayUseItemAt(pos, net.minecraft.core.Direction.UP, stack)
                && player.getEyePosition().distanceToSqr(LinkRegistry.worldPosition(level, pos)) <= reach * reach;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            if (!context.getLevel().isClientSide) {
                clear(context.getItemInHand());
                player.displayClientMessage(Component.translatable("teutonlib.peripheral_link.cleared"), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (context.getLevel() instanceof ServerLevel level) {
            if (!allowed(player, level, context.getClickedPos(), context.getItemInHand())) return InteractionResult.FAIL;
            BlockEntity target = level.getBlockEntity(context.getClickedPos());
            if (LinkRegistry.isTarget(target) && selection(context.getItemInHand()) == null) {
                LinkIdentity identity = (LinkIdentity) target;
                UUID id = identity.teutonlib$linkTarget();
                if (id != null) LinkRegistry.anchor(level, id); // Detect copied anchors before explicit re-pairing.
                if (id == null || LinkConflicts.get(level.getServer()).contains(id)) {
                    id = UUID.randomUUID(); identity.teutonlib$linkTarget(id);
                }
                final UUID selected = id;
                CustomData.update(DataComponents.CUSTOM_DATA, context.getItemInHand(), tag -> {
                    tag.putUUID("TeutonLibSelectedTarget", selected);
                    tag.putString("TeutonLibSelectedDimension", level.dimension().location().toString());
                });
                player.displayClientMessage(Component.translatable("teutonlib.peripheral_link.selected", selected.toString()), true);
                return InteractionResult.CONSUME;
            }
        } else if (selection(context.getItemInHand()) == null && LinkRegistry.isTarget(context.getLevel().getBlockEntity(context.getClickedPos()))) return InteractionResult.SUCCESS;
        return super.useOn(context);
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level) {
            Player player = context.getPlayer();
            if (player == null || !allowed(player, level, context.getClickedPos(), context.getItemInHand())) return InteractionResult.FAIL;
            UUID selected = selection(context.getItemInHand());
            BlockEntity target = LinkRegistry.anchor(level, selected);
            double range = ServerConfig.PERIPHERAL_LINK_RANGE.get();
            if (target == null || target.getLevel() != level || LinkRegistry.worldPosition(target)
                    .distanceToSqr(LinkRegistry.worldPosition(level, context.getClickedPos())) > range * range) {
                player.displayClientMessage(Component.translatable("teutonlib.peripheral_link.invalid_target"), true);
                return InteractionResult.FAIL;
            }
        }
        InteractionResult result = super.place(context);
        if (!context.getLevel().isClientSide && result.consumesAction()) clear(context.getItemInHand());
        return result;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                clear(player.getItemInHand(hand));
                player.displayClientMessage(Component.translatable("teutonlib.peripheral_link.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        return super.use(level, player, hand);
    }
    @Override protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack,
                                                          net.minecraft.world.level.block.state.BlockState state) {
        // A placed link always gets a fresh identity; block-entity data on an item cannot restore a broken link.
        return false;
    }
}
