package de.TeutonStudio.TeutonLib.integration.peripheral

import de.TeutonStudio.TeutonLib.api.sable.SubLevelInteractionGeometry
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

/** Slotless, server-authoritative menu for one loaded Peripheral Link. */
class PeripheralLinkMenu private constructor(
    containerId: Int,
    private val level: ServerLevel?,
    val position: BlockPos,
    private val identity: UUID?
) : AbstractContainerMenu(PeripheralLinks.MENU.get(), containerId) {
    private var session: UUID? = null
    constructor(containerId: Int, inventory: Inventory, link: PeripheralLinkBlockEntity) : this(
        containerId,
        link.level as? ServerLevel,
        link.blockPos.immutable(),
        link.identity()
    )

    constructor(containerId: Int, inventory: Inventory, position: BlockPos) : this(
        containerId,
        null,
        position.immutable(),
        null
    )

    fun link(player: ServerPlayer): PeripheralLinkBlockEntity? {
        val serverLevel = level ?: return null
        if (!serverLevel.hasChunkAt(position) || !SubLevelInteractionGeometry.withinReach(player, serverLevel, position)) return null
        val link = LinkRegistry.blockAt(serverLevel, position) as? PeripheralLinkBlockEntity ?: return null
        return link.takeIf { !it.isRemoved && it.identity() == identity }
    }

    fun acceptsSession(candidate: UUID): Boolean {
        if (session == null) session = candidate
        return session == candidate
    }

    override fun stillValid(player: Player): Boolean {
        val serverPlayer = player as? ServerPlayer ?: return true
        val link = link(serverPlayer) ?: return false
        val serverLevel = link.level as? ServerLevel ?: return false
        return SubLevelInteractionGeometry.mayInteract(player, serverLevel, position)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY
}
