package de.TeutonStudio.TeutonLib.integration.peripheral

import de.TeutonStudio.TeutonLib.api.sable.SubLevelInteractionGeometry
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

class LinkAntennaMenu(val position: BlockPos, val target: UUID, private val level: ServerLevel?, containerId: Int) :
    AbstractContainerMenu(PeripheralLinks.ANTENNA_MENU.get(), containerId) {
    private var session: UUID? = null
    fun accepts(candidate: UUID): Boolean {
        if (session == null) session = candidate
        return session == candidate
    }
    fun anchor(player: ServerPlayer): BlockEntity? {
        val level = level ?: return null
        if (player.level() !== level || !SubLevelInteractionGeometry.withinReach(player, level, position) ||
            !SubLevelInteractionGeometry.mayInteract(player, level, position)) return null
        return LinkRegistry.anchor(level, target)?.takeIf { it.blockPos == position }
    }
    override fun stillValid(player: Player): Boolean = player !is ServerPlayer || anchor(player) != null
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    companion object {
        fun open(player: ServerPlayer, anchor: BlockEntity, target: UUID) {
            player.openMenu(object : MenuProvider {
                override fun getDisplayName(): Component = Component.translatable("teutonlib.antenna.title")
                override fun createMenu(id: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
                    LinkAntennaMenu(anchor.blockPos.immutable(), target, anchor.level as ServerLevel, id)
            }) { buffer -> buffer.writeBlockPos(anchor.blockPos); buffer.writeUUID(target) }
        }
    }
}
