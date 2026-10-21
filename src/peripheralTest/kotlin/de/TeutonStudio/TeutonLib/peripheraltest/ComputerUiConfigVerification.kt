package de.TeutonStudio.TeutonLib.peripheraltest

import dan200.computercraft.client.gui.AbstractComputerScreen
import dan200.computercraft.client.gui.ComputerScreen
import dan200.computercraft.client.gui.widgets.TerminalWidget
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.computer.core.ComputerFamily
import dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory
import dan200.computercraft.shared.computer.terminal.NetworkedTerminal
import dan200.computercraft.shared.computer.terminal.TerminalState
import dan200.computercraft.shared.network.container.ComputerContainerData
import de.TeutonStudio.TeutonLib.ClientConfig
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.client.computer.WorkspacePanels
import de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs
import example.teutonlib.client.TemplateComputerScreen
import java.lang.AssertionError
import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

/** Exercises config changes with actual CC widgets, without a world or saved config edits.  */
@EventBusSubscriber(modid = "teutonlib_peripheral_test", value = [Dist.CLIENT])
object ComputerUiConfigVerification {
    private var done = false

    @SubscribeEvent
    @JvmStatic
    fun tick(event: ClientTickEvent.Post?) {
        val mc = Minecraft.getInstance()
        if (done || !java.lang.Boolean.getBoolean("teutonlib.computerUiConfigTests") || mc.screen == null || mc.overlay != null) return
        done = true
        val previous = ClientConfig.COMPUTER_UI_ENABLED.get()
        val report = Path.of("../../build/reports/smoke/computer-ui-config.txt")
        try {
            Files.deleteIfExists(report)
            verifyConfigEditor(mc)
            if (!ClientConfig.COMPUTER_UI_ENABLED.default) throw AssertionError("UI must default to enabled")
            for (template in booleanArrayOf(false, true)) for (family in arrayOf<ComputerFamily>(
                ComputerFamily.NORMAL,
                ComputerFamily.ADVANCED
            )) {
                val inventory = Inventory(null)
                val terminal = NetworkedTerminal(51, 19, family == ComputerFamily.ADVANCED)
                val stack = if (template) ItemStack(Items.STONE) else ItemStack(
                    if (family == ComputerFamily.NORMAL)
                        ModRegistry.Items.COMPUTER_NORMAL.get()
                    else
                        ModRegistry.Items.COMPUTER_ADVANCED.get()
                )
                val menu = ComputerMenuWithoutInventory(
                    ModRegistry.Menus.COMPUTER.get(), 7, inventory,
                    ComputerContainerData(family, TerminalState.create(terminal), stack, 1024)
                )
                ClientConfig.COMPUTER_UI_ENABLED.set(false)
                val screen: AbstractComputerScreen<*> = if (template)
                    TemplateComputerScreen<ComputerMenuWithoutInventory>(
                        menu,
                        inventory,
                        Component.literal("Config test")
                    )
                else
                    ComputerScreen<ComputerMenuWithoutInventory>(menu, inventory, Component.literal("Config test"))
                mc.setScreen(screen)
                check(screen, false)
                ClientConfig.COMPUTER_UI_ENABLED.set(true)
                screen.containerTick()
                check(screen, true)
                val panels = (screen as WorkspacePanels.Owner).`teutonlib$workspace`()!!
                val tab: ComputerSidebarTabs.Tab = screen.children().stream()
                    .filter { o: kotlin.Any? -> ComputerSidebarTabs.Tab::class.java.isInstance(o) }
                    .map<ComputerSidebarTabs.Tab> { obj: kotlin.Any? -> ComputerSidebarTabs.Tab::class.java.cast(obj) }
                    .skip(1)
                    .findFirst().orElseThrow()!!
                screen.mouseClicked((tab.x + 8).toDouble(), (tab.y + 8).toDouble(), 0)
                if (panels.page() != 2) throw AssertionError("Channels did not open")
                ClientConfig.COMPUTER_UI_ENABLED.set(false)
                screen.containerTick()
                check(screen, false)
                mc.resizeDisplay()
                check(screen, false)
                ClientConfig.COMPUTER_UI_ENABLED.set(true)
                screen.containerTick()
                check(screen, true)
            }
            Files.createDirectories(report.parent)
            Files.writeString(
                report,
                "PASS: registered NeoForge config editor; client navigation and toggle; unloaded server config locked; default on; native/template normal/advanced; disabled startup; enable, disable on channels, resize, re-enable; terminal restored\n"
            )
        } catch (error: Throwable) {
            TeutonLib.Companion.LOGGER.error("Computer UI config verification failed", error)
        } finally {
            ClientConfig.COMPUTER_UI_ENABLED.set(previous)
            mc.setScreen(null)
            mc.stop()
        }
    }

    /** Exercise the actual registered editor, including nested sections and a real toggle. */
    private fun verifyConfigEditor(mc: Minecraft) {
        val container = net.neoforged.fml.ModList.get().getModContainerById(TeutonLib.MOD_ID).orElseThrow()
        val factory = container.getCustomExtension(
            net.neoforged.neoforge.client.gui.IConfigScreenFactory::class.java
        ).orElseThrow { AssertionError("No config screen registered") }
        val parent = mc.screen!!
        val editor = factory.createScreen(container, parent)
        mc.setScreen(editor)
        val clientLabel = Component.translatable("teutonlib.configuration.section.teutonlib.client.toml").string
        val serverLabel = Component.translatable("teutonlib.configuration.section.teutonlib.server.toml").string
        val buttons = descendants(editor).filterIsInstance<net.minecraft.client.gui.components.Button>().toList()
        val client = buttons.single { it.message.string.contains(clientLabel) }
        val server = buttons.single { it.message.string.contains(serverLabel) }
        check(client.active) { "Client config is locked" }
        check(!server.active) { "Unloaded world config must be locked" }
        client.onPress()
        val clientSection = mc.screen!!
        check(clientSection is net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen)
        // The only subsection button uses the translated section label.
        descendants(clientSection).filterIsInstance<net.minecraft.client.gui.components.Button>()
            .first { it.active && it.message.string == Component.translatable(
                "neoforge.configuration.uitext.section",
                Component.translatable("teutonlib.configuration.computerUi.button")
            ).string }.onPress()
        val preferenceSection = mc.screen!!
        check(preferenceSection !== clientSection) { "Computer UI subsection did not open" }
        val toggle = descendants(preferenceSection)
            .filterIsInstance<net.minecraft.client.gui.components.CycleButton<*>>().single()
        val previous = ClientConfig.COMPUTER_UI_ENABLED.get()
        toggle.onPress()
        check(ClientConfig.COMPUTER_UI_ENABLED.get() != previous) { "Editor did not change UI preference" }
        toggle.onPress()
        check(ClientConfig.COMPUTER_UI_ENABLED.get() == previous) { "Editor did not restore UI preference" }
        preferenceSection.onClose()
        clientSection.onClose()
        editor.onClose()
        check(mc.screen === parent) { "Editor did not return to parent" }
    }

    private fun descendants(node: net.minecraft.client.gui.components.events.GuiEventListener):
        Sequence<net.minecraft.client.gui.components.events.GuiEventListener> = sequence {
        yield(node)
        if (node is net.minecraft.client.gui.components.events.ContainerEventHandler) {
            node.children().forEach { yieldAll(descendants(it)) }
        }
    }

    @JvmStatic
    private fun check(screen: AbstractComputerScreen<*>, enabled: Boolean) {
        val tabs =
            screen.children().stream().filter { o: Any? -> ComputerSidebarTabs.Tab::class.java.isInstance(o) }.count()
        if (tabs != (if (enabled) 2 else 0).toLong()) throw AssertionError("Unexpected tabs: " + tabs)
        if (((screen as WorkspacePanels.Owner).`teutonlib$workspace`() != null) != enabled) throw AssertionError("Stale controller")
        val terminal: TerminalWidget =
            screen.children().stream().filter { o: kotlin.Any? -> TerminalWidget::class.java.isInstance(o) }
                .map<TerminalWidget> { obj: kotlin.Any? -> TerminalWidget::class.java.cast(obj) }.findFirst()
                .orElseThrow()!!
        if (!terminal.visible || !terminal.active || screen.focused !== terminal) throw AssertionError("Terminal not restored")
    }
}
