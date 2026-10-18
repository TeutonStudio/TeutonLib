package de.TeutonStudio.TeutonLib.integration.peripheral

import de.TeutonStudio.TeutonLib.ServerConfig
import java.util.UUID
import java.util.function.Consumer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class PeripheralLinkItem(block: Block, properties: Properties) : BlockItem(block, properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player
        if (player == null) return InteractionResult.FAIL
        if (player.isShiftKeyDown()) {
            if (!context.level.isClientSide) {
                clear(context.itemInHand)
                player.displayClientMessage(Component.translatable("teutonlib.peripheral_link.cleared"), true)
            }
            return InteractionResult.sidedSuccess(context.level.isClientSide)
        }
        val level = context.level
        if (level is ServerLevel) {
            if (!allowed(player, level, context.clickedPos, context.itemInHand)) return InteractionResult.FAIL
            val target: BlockEntity? = level.getBlockEntity(context.clickedPos)
            if (LinkRegistry.isTarget(target) && selection(context.itemInHand) == null) {
                val identity = target as LinkIdentity?
                var id = identity!!.`teutonlib$linkTarget`()
                if (id != null) LinkRegistry.anchor(level, id) // Detect copied anchors before explicit re-pairing.

                if (id == null || LinkConflicts.Companion.get(level.server).contains(id)) {
                    id = UUID.randomUUID()
                    identity.`teutonlib$linkTarget`(id)
                }
                val selected = id
                CustomData.update(DataComponents.CUSTOM_DATA, context.itemInHand, Consumer { tag: CompoundTag? ->
                    tag!!.putUUID("TeutonLibSelectedTarget", selected)
                    tag.putString("TeutonLibSelectedDimension", level.dimension().location().toString())
                })
                player.displayClientMessage(
                    Component.translatable(
                        "teutonlib.peripheral_link.selected",
                        selected.toString()
                    ), true
                )
                return InteractionResult.CONSUME
            }
        } else if (selection(context.itemInHand) == null && LinkRegistry.isTarget(
                context.level.getBlockEntity(context.clickedPos)
            )
        ) return InteractionResult.SUCCESS
        return super.useOn(context)
    }

    override fun place(context: BlockPlaceContext): InteractionResult {
        val level = context.level
        if (level is ServerLevel) {
            val player = context.player
            if (player == null || !allowed(
                    player,
                    level,
                    context.clickedPos,
                    context.itemInHand
                )
            ) return InteractionResult.FAIL
            val selected: UUID? = selection(context.itemInHand)
            val target = LinkRegistry.anchor(level, selected)
            val range = ServerConfig.PERIPHERAL_LINK_RANGE.get().toDouble()
            if (target == null || target.level !== level || LinkRegistry.worldPosition(target)
                    .distanceToSqr(LinkRegistry.worldPosition(level, context.clickedPos)) > range * range
            ) {
                player.displayClientMessage(Component.translatable("teutonlib.peripheral_link.invalid_target"), true)
                return InteractionResult.FAIL
            }
        }
        val result = super.place(context)
        if (!context.level.isClientSide && result.consumesAction()) clear(context.itemInHand)
        return result
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                clear(player.getItemInHand(hand))
                player.displayClientMessage(Component.translatable("teutonlib.peripheral_link.cleared"), true)
            }
            return InteractionResultHolder.sidedSuccess<ItemStack>(player.getItemInHand(hand), level.isClientSide)
        }
        return super.use(level, player, hand)
    }

    override fun updateCustomBlockEntityTag(
        pos: BlockPos, level: Level, player: Player?, stack: ItemStack,
        state: BlockState
    ): Boolean {
        // A placed link always gets a fresh identity; block-entity data on an item cannot restore a broken link.
        return false
    }

    companion object {
        @JvmStatic
        fun selection(stack: ItemStack): UUID? {
            val tag = stack.getOrDefault<CustomData>(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            return if (tag.hasUUID("TeutonLibSelectedTarget")) tag.getUUID("TeutonLibSelectedTarget") else null
        }

        @JvmStatic
        private fun clear(stack: ItemStack) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, Consumer { tag: CompoundTag? ->
                tag!!.remove("TeutonLibSelectedTarget")
                tag.remove("TeutonLibSelectedDimension")
            })
        }

        @JvmStatic
        private fun allowed(player: Player, level: ServerLevel, pos: BlockPos, stack: ItemStack): Boolean {
            val reach = player.blockInteractionRange() + 1
            return level.hasChunkAt(pos) && level.mayInteract(player, pos)
                    && player.mayUseItemAt(pos, Direction.UP, stack)
                    && player.eyePosition.distanceToSqr(LinkRegistry.worldPosition(level, pos)) <= reach * reach
        }
    }
}
