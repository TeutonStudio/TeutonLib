package de.TeutonStudio.TeutonLib.api.computer.client

import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace
import java.util.concurrent.CopyOnWriteArrayList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

data class ComputerWorkspaceClientContext(
    val screen: Screen,
    val showTerminal: Runnable
)

data class ComputerWorkspaceClientTab(
    val label: Component,
    val icon: ResourceLocation,
    val hoverIcon: ResourceLocation,
    val activate: (ComputerWorkspaceClientContext) -> Unit
)

/** Client-only description of an addon workspace hosted by a normal CC computer screen. */
interface ComputerWorkspaceClientDescriptor {
    val workspace: ResourceLocation
    fun matches(menu: AbstractComputerMenu): Boolean
    fun tabs(context: ComputerWorkspaceClientContext): List<ComputerWorkspaceClientTab> = emptyList()
    fun handleAction(
        context: ComputerWorkspaceClientContext,
        action: ComputerWorkspace.Action,
        entry: ComputerWorkspace.Entry?
    ) = Unit
}

/** Register from client setup. The returned handle removes the descriptor. */
object ComputerWorkspaceClients {
    private val descriptors = CopyOnWriteArrayList<ComputerWorkspaceClientDescriptor>()

    @JvmStatic
    fun register(descriptor: ComputerWorkspaceClientDescriptor): AutoCloseable {
        descriptors.add(descriptor)
        return AutoCloseable { descriptors.remove(descriptor) }
    }

    @JvmStatic
    fun resolve(menu: AbstractComputerMenu): ComputerWorkspaceClientDescriptor? = descriptors.firstOrNull { descriptor ->
        try {
            descriptor.matches(menu)
        } catch (failure: RuntimeException) {
            TeutonLib.LOGGER.warn("Computer workspace client descriptor failed", failure)
            false
        }
    }
}
