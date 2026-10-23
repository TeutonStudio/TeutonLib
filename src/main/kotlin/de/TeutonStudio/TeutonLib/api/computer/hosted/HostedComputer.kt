package de.TeutonStudio.TeutonLib.api.computer.hosted

import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.computer.core.ComputerFamily
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.computer.core.ServerContext
import dan200.computercraft.shared.computer.core.TerminalSize
import dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory
import dan200.computercraft.shared.util.NonNegativeId
import dan200.computercraft.shared.util.StorageCapacity
import java.util.ArrayDeque
import java.util.UUID
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

data class HostedComputerPersistenceKeys(
    val computerId: String,
    val label: String,
    val capacity: String,
    val terminalWidth: String,
    val terminalHeight: String,
    val powered: String
)

/** Reusable CC:Tweaked runtime hosted by an addon block entity instead of a CC computer block. */
class HostedComputer(
    private val host: BlockEntity,
    private val installed: ItemStack,
    val family: ComputerFamily,
    initialPowered: Boolean = false,
    private val configure: (ServerComputer.Properties) -> ServerComputer.Properties = { it },
    private val refreshInputs: (ServerComputer) -> Unit = {},
    private val changed: () -> Unit = {},
    private val maxPendingEvents: Int = 64
) : AutoCloseable {
    private data class PendingEvent(val name: String, val arguments: Array<Any?>)

    private var instanceId: UUID? = null
    var computerId: Int = NonNegativeId.getId(installed.get(ModRegistry.DataComponents.COMPUTER_ID.get()))
        private set
    var label: String? = installed.get(DataComponents.CUSTOM_NAME)?.string
        private set
    var storageCapacity: Long = StorageCapacity.getOrDefault(
        installed.get(ModRegistry.DataComponents.STORAGE_CAPACITY.get()), -1
    )
        private set
    var terminalSize: TerminalSize? = installed.get(ModRegistry.DataComponents.TERMINAL_SIZE.get())
        private set
    var powered: Boolean = initialPowered
        private set
    private var startOn = initialPowered
    private val pending = ArrayDeque<PendingEvent>()
    private val menuAccess = hashMapOf<UUID, (Player) -> Boolean>()

    fun serverComputer(): ServerComputer? {
        val level = host.level as? ServerLevel ?: return null
        return instanceId?.let { ServerContext.get(level.server).registry().get(it) }
    }

    fun createServerComputer(): ServerComputer {
        val level = host.level as? ServerLevel
            ?: throw IllegalStateException("Cannot create a server computer outside a ServerLevel")
        val context = ServerContext.get(level.server)
        instanceId?.let { context.registry().get(it) }?.let { return it }
        if (computerId < 0) {
            computerId = ComputerCraftAPI.createUniqueNumberedSaveDir(level.server, "computer")
            changed()
        }
        val base = ServerComputer.properties(computerId, family)
            .label(label)
            .terminalSize(terminalSize ?: defaultTerminalSize())
            .storageCapacity(storageCapacity)
        val computer = ServerComputer(level, host.blockPos, configure(base))
        instanceId = computer.register()
        refreshInputs(computer)
        return computer
    }

    /** Ticks an existing/remembered runtime and returns null for a pristine powered-off host. */
    fun tick(): ServerComputer? {
        val level = host.level as? ServerLevel ?: return null
        val computer = serverComputer() ?: if (computerId >= 0 || startOn || pending.isNotEmpty()) {
            createServerComputer()
        } else return null
        computer.setPosition(level, host.blockPos)
        if (startOn || powered || pending.isNotEmpty()) {
            computer.turnOn(); startOn = false
        }
        refreshInputs(computer)
        computer.keepAlive()
        val nextPowered = computer.isOn
        val nextLabel = computer.label
        if (powered != nextPowered || label != nextLabel) {
            powered = nextPowered; label = nextLabel; changed()
        }
        if (nextPowered) flush(computer)
        return computer
    }

    fun queueEventWhenReady(name: String, vararg arguments: Any?) {
        val computer = serverComputer() ?: createServerComputer()
        val copied = arrayOf(*arguments)
        if (computer.isOn) { computer.queueEvent(name, copied); return }
        while (pending.size >= maxPendingEvents) pending.removeFirst()
        pending.addLast(PendingEvent(name, copied))
        computer.turnOn()
    }

    fun prepareMenu(player: Player, usable: (Player) -> Boolean): ServerComputer {
        val computer = createServerComputer()
        computer.turnOn(); powered = true
        menuAccess[player.uuid] = usable
        return computer
    }

    fun createMenu(id: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
        ComputerMenuWithoutInventory(
            ModRegistry.Menus.COMPUTER.get(), id, inventory,
            menuAccess.remove(player.uuid) ?: { false }, createServerComputer()
        )

    fun writeStandardComponents(stack: ItemStack) {
        stack.set(ModRegistry.DataComponents.COMPUTER_ID.get(), NonNegativeId.of(computerId))
        stack.set(ModRegistry.DataComponents.STORAGE_CAPACITY.get(), storageCapacity.takeIf { it > 0 }?.let(::StorageCapacity))
        stack.set(ModRegistry.DataComponents.TERMINAL_SIZE.get(), terminalSize)
        stack.set(DataComponents.CUSTOM_NAME, label?.let(Component::literal))
    }

    fun write(tag: CompoundTag, keys: HostedComputerPersistenceKeys) {
        if (computerId >= 0) tag.putInt(keys.computerId, computerId)
        label?.let { tag.putString(keys.label, it) }
        if (storageCapacity > 0) tag.putLong(keys.capacity, storageCapacity)
        terminalSize?.let { tag.putInt(keys.terminalWidth, it.width()); tag.putInt(keys.terminalHeight, it.height()) }
        tag.putBoolean(keys.powered, powered)
    }

    fun read(tag: CompoundTag, keys: HostedComputerPersistenceKeys, clientPacket: Boolean) {
        computerId = if (tag.contains(keys.computerId)) tag.getInt(keys.computerId) else -1
        label = tag.getString(keys.label).takeIf(String::isNotEmpty)
        storageCapacity = if (tag.contains(keys.capacity)) tag.getLong(keys.capacity) else -1
        terminalSize = if (tag.contains(keys.terminalWidth) && tag.contains(keys.terminalHeight)) {
            TerminalSize(tag.getInt(keys.terminalWidth), tag.getInt(keys.terminalHeight))
        } else null
        powered = tag.getBoolean(keys.powered)
        if (!clientPacket) startOn = powered
    }

    override fun close() {
        pending.clear(); menuAccess.clear(); serverComputer()?.close(); instanceId = null
    }

    private fun flush(computer: ServerComputer) {
        while (computer.isOn && pending.isNotEmpty()) {
            val event = pending.removeFirst(); computer.queueEvent(event.name, event.arguments)
        }
    }

    private fun defaultTerminalSize(): TerminalSize = TerminalSize(
        dan200.computercraft.shared.config.ConfigSpec.computerTermWidth.get(),
        dan200.computercraft.shared.config.ConfigSpec.computerTermHeight.get()
    )
}
