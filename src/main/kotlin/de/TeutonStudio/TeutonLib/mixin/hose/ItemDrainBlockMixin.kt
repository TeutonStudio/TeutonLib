package de.TeutonStudio.TeutonLib.mixin.hose

import com.simibubi.create.content.fluids.drain.ItemDrainBlock
import de.TeutonStudio.TeutonLib.api.sable.SubLevelInteractionGeometry
import de.TeutonStudio.TeutonLib.integration.hose.HoseConnectorRegistry
import de.TeutonStudio.TeutonLib.integration.hose.HoseDrainConnection
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import de.TeutonStudio.TeutonLib.integration.sable.HoseConnectorPhysics
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ItemDrainBlock::class, remap = false)
abstract class ItemDrainBlockMixin : BlockSubLevelAssemblyListener {
    @Inject(method = ["useItemOn"], at = [At("HEAD")], cancellable = true)
    private fun `teutonlib$useHose`(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos,
        player: Player, hand: InteractionHand, hit: BlockHitResult, cir: CallbackInfoReturnable<ItemInteractionResult>) {
        if (BuiltInRegistries.ITEM.getKey(stack.item).toString() != "create:wrench") return
        val connection = HoseDrainConnection.get(level.getBlockEntity(pos)) ?: return
        // Empty drains keep Create's ordinary wrench and item interactions.
        if (connection.headId == null && HoseHeadEntity.carriedBy(level, player) == null) return
        if (level.isClientSide) {
            cir.returnValue = ItemInteractionResult.SUCCESS
            return
        }
        if (!player.isAlive || player.isSpectator || !SubLevelInteractionGeometry.withinReach(player, level, pos, 0.0) ||
            !SubLevelInteractionGeometry.mayInteract(player, level, pos)) {
            cir.returnValue = ItemInteractionResult.FAIL
            return
        }
        if (connection.headId != null) {
            val head = connection.head()
            if (head == null) cir.returnValue = ItemInteractionResult.FAIL
            else {
                connection.release()
                head.carry(player, hand)
                cir.returnValue = ItemInteractionResult.SUCCESS
            }
        } else {
            val head = HoseHeadEntity.carriedBy(level, player)
            cir.returnValue = if (head != null && connection.dock(head, player)) ItemInteractionResult.SUCCESS else ItemInteractionResult.FAIL
        }
    }

    @Inject(method = ["onRemove"], at = [At("HEAD")])
    private fun `teutonlib$releaseHose`(state: BlockState, level: Level, pos: BlockPos, next: BlockState,
        moving: Boolean, ci: CallbackInfo) {
        if (!level.isClientSide && state.block !== next.block) HoseDrainConnection.get(level.getBlockEntity(pos))?.release()
    }

    override fun beforeMove(from: ServerLevel, to: ServerLevel, state: BlockState, source: BlockPos, target: BlockPos) {
        if (from !== to) HoseDrainConnection.get(from.getBlockEntity(source))?.release()
    }

    override fun afterMove(from: ServerLevel, to: ServerLevel, state: BlockState, source: BlockPos, target: BlockPos) {
        val connection = HoseDrainConnection.get(to.getBlockEntity(target)) ?: return
        HoseConnectorRegistry.register(connection)
        HoseConnectorPhysics.register(connection)
        connection.head()?.connector()
    }
}
