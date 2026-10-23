package de.TeutonStudio.TeutonLib.peripheraltest

import dan200.computercraft.client.gui.ComputerScreen
import dan200.computercraft.client.gui.widgets.TerminalWidget
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.computer.core.ComputerFamily
import dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory
import dan200.computercraft.shared.computer.terminal.*
import dan200.computercraft.shared.computer.terminal.NetworkedTerminal
import dan200.computercraft.shared.computer.terminal.TerminalState
import dan200.computercraft.shared.network.container.ComputerContainerData
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace
import de.TeutonStudio.TeutonLib.api.computer.SourceApi
import de.TeutonStudio.TeutonLib.api.computer.SourceMethod
import de.TeutonStudio.TeutonLib.api.computer.ModemDetails
import de.TeutonStudio.TeutonLib.integration.computer.StandardComputerWorkspace
import de.TeutonStudio.TeutonLib.client.computer.ComputerPanels
import de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs
import de.TeutonStudio.TeutonLib.client.computer.WorkspacePanels
import de.TeutonStudio.TeutonLib.integration.computer.ComputerPayloads
import de.TeutonStudio.TeutonLib.integration.computer.WorkspacePayloads
import example.teutonlib.client.TemplateComputerScreen
import java.io.IOException
import java.lang.AssertionError
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import org.lwjgl.glfw.GLFW

/** Opt-in renderer/focus verification. Does not connect to or alter a real world.  */
@EventBusSubscriber(modid = "teutonlib_peripheral_test", value = [Dist.CLIENT])
object ComputerUiVerification {
    private var stage = 0
    private var ticks = 0

    @JvmStatic
    private fun verifyWorkspace(screen: AbstractContainerScreen<*>, scenario: Int) {
        val panels = (screen as WorkspacePanels.Owner).`teutonlib$workspace`()!!
        val backend = if (scenario >= 8) de.teutonstudio.ccaeroworks.compat.teutonlib.DeskComputerWorkspace.ID else StandardComputerWorkspace.ID
        if (panels == null) throw AssertionError("Aeroworks workspace missing")
        val tabs: MutableList<ComputerSidebarTabs.Tab> =
            screen.children().stream().filter { c: GuiEventListener? -> c is ComputerSidebarTabs.Tab }
                .map<ComputerSidebarTabs.Tab> { c: GuiEventListener? -> c as ComputerSidebarTabs.Tab }.toList()
        if (tabs.size != 3) throw AssertionError("Unexpected workspace sidebar: " + tabs.size)
        val labels = tabs.map { it.message.string }
        val expectedLabels = listOf(
            Component.translatable("teutonlib.computer.standard").string,
            Component.translatable("teutonlib.computer.sources").string,
            Component.translatable("teutonlib.computer.channels").string
        )
        if (labels != expectedLabels) throw AssertionError("Workspace tab order: $labels")
        val page = if (scenario % 2 == 0) 2 else 1
        val tab = tabs[page]
        screen.mouseClicked((tab.x + 8).toDouble(), (tab.y + 10).toDouble(), 0)
        if (panels.page() != page) throw AssertionError("Aeroworks tab order/state")
        val rename = ComputerWorkspace.Action(
            if (page == 1) "source_nickname" else "rename",
            if (page == 1) "teutonlib.computer.set_nickname" else "Rename",
            true,
            false,
            false
        )
        val child = ComputerWorkspace.Entry(
            "wire:stable", "Engine", "12/15 · 2 connections", "", "wire:stable", true, false,
            mutableListOf<ComputerWorkspace.Entry>(), java.util.List.of<ComputerWorkspace.Action>(rename), CompoundTag().also {
                if (page == 1) it.put("api", SourceApi(
                    "left", listOf("modem"), "local",
                    (0..40).map { SourceMethod("method$it", "method$it(value: number)", "Long method documentation to exercise wrapping in the source API window.") },
                    ModemDetails("ender", "minecraft:overworld", 256.0, true)
                ).encode())
                if (page == 1) it.putString("nickname", "Engine")
            }
        )
        val entry = ComputerWorkspace.Entry(
            "section", if (page == 1) "Display scripts" else "Wire channels", "", "", "", false, false,
            java.util.List.of<ComputerWorkspace.Entry>(child), mutableListOf<ComputerWorkspace.Action>(), CompoundTag()
        )
        val add = ComputerWorkspace.Action("add", "+Wire", true, false, false)
        val snapshot = ComputerWorkspace.Snapshot(
            if (page == 1) "Sources" else "Channels",
            "Live",
            java.util.List.of<ComputerWorkspace.Entry>(entry),
            if (page == 2) java.util.List.of<ComputerWorkspace.Action>(add) else mutableListOf<ComputerWorkspace.Action>()
        )
        panels.receive(WorkspacePayloads.Response(7, panels.session(), backend, page, snapshot.encode()))
        val rows: AbstractWidget = screen.children().stream()
            .filter { c: GuiEventListener -> c.javaClass.name.endsWith("WorkspacePanels\$Rows") }
            .map<AbstractWidget> { c: GuiEventListener? -> c as AbstractWidget }.findFirst().orElseThrow()!!
        screen.mouseClicked((rows.x + 30).toDouble(), (rows.y + 52).toDouble(), 0)
        val terminal: TerminalWidget = screen.children().stream().filter { c: GuiEventListener? -> c is TerminalWidget }
            .map<TerminalWidget> { c: GuiEventListener? -> c as TerminalWidget }.findFirst().orElseThrow()!!
        if (terminal.active || terminal.visible) throw AssertionError("Terminal still accepts input under addon page")
        if (page == 1) {
            val field = screen.children().filterIsInstance<EditBox>().first()
            if (screen.focused !== field || !field.isFocused() || field.value != "Engine")
                throw AssertionError("Source nickname editor focus/value missing")
            val infoX = (field.x - 18).toDouble()
            val infoY = (rows.y + 52).toDouble()
            screen.mouseClicked(infoX, infoY, 0)
            if (!panels.sourceApiOpen()) throw AssertionError("Source info did not open API")
            screen.mouseScrolled(infoX, infoY, 0.0, -4.0)
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0)
            if (panels.sourceApiOpen() || panels.page() != 1 || Minecraft.getInstance().screen !== screen)
                throw AssertionError("API Escape did not preserve source page")
            screen.mouseClicked(infoX, infoY, 0)
            if (!panels.sourceApiOpen()) throw AssertionError("Source disappeared after API close")
            screen.mouseClicked((rows.x + rows.width - 20).toDouble(), (rows.y + 35).toDouble(), 0)
            if (panels.sourceApiOpen() || panels.page() != 1) throw AssertionError("API close icon changed page")
            screen.mouseClicked(infoX, infoY, 0)
        }
        if (page == 2) {
            screen.keyPressed(342, 0, 0) // Host must apply the requested editor focus after a response.
            val field: EditBox = screen.children().stream().filter { c: GuiEventListener? -> c is EditBox }
                .map<EditBox> { c: GuiEventListener? -> c as EditBox }.findFirst().orElseThrow()!!
            if (screen.focused !== field || !field.isFocused()) throw AssertionError("Workspace name focus missing")
            field.setValue("")
            screen.keyPressed(69, 0, 0)
            screen.charTyped('e', 0)
            if (field.value != "e" || Minecraft.getInstance().screen !== screen) throw AssertionError("Workspace inventory key interrupted editor")
        }
    }

    @JvmStatic
    private fun finishWorkspace(mc: Minecraft) {
        val screen = mc.screen
        var panels = (screen as WorkspacePanels.Owner).`teutonlib$workspace`()!!
        if (panels.sourceApiOpen()) {
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0)
            if (panels.sourceApiOpen() || panels.page() != 1) throw AssertionError("API close changed page")
        }
        val page = panels.page()
        val session = panels.session()
        mc.resizeDisplay()
        panels = (screen as WorkspacePanels.Owner).`teutonlib$workspace`()!!
        if (panels.page() != page || session == panels.session()) throw AssertionError("Workspace resize state/session")
        screen.keyPressed(256, 0, 0)
        if (mc.screen !== screen || panels.page() != 0) throw AssertionError("Workspace Escape closed screen")
        if (screen.focused !is TerminalWidget) throw AssertionError("Workspace terminal focus not restored")
        val tabs = screen.children().filterIsInstance<ComputerSidebarTabs.Tab>()
        screen.mouseClicked((tabs[page].x + 8).toDouble(), (tabs[page].y + 8).toDouble(), 0)
        if (panels.page() != page) throw AssertionError("Workspace page did not reopen")
        screen.mouseClicked((tabs[0].x + 8).toDouble(), (tabs[0].y + 8).toDouble(), 0)
        if (panels.page() != 0 || screen.focused !is TerminalWidget) {
            throw AssertionError("Standard tab did not restore terminal")
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun tick(event: ClientTickEvent.Post?) {
        if (!java.lang.Boolean.getBoolean("teutonlib.computerUiTests")) return
        val mc = Minecraft.getInstance()
        if (stage == 0) {
            if (mc.screen == null || mc.overlay != null) return
            mc.window.setWindowed(1280, 800)
            stage = 1
            ticks = 0
            try {
                Files.deleteIfExists(Path.of("../../build/reports/smoke/computer-ui.txt"))
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
        (mc.screen as? WorkspacePanels.Owner)?.`teutonlib$workspace`()?.tick()
        if (++ticks < 40) return
        ticks = 0
        val scenarioCount = if (net.neoforged.fml.ModList.get().isLoaded("cc_aeroworks")) 12 else 8
        try {
            if (stage % 2 == 1 && stage <= scenarioCount * 2 - 1) {
                val scenario = (stage - 1) / 2
                mc.options.guiScale().set(if (scenario % 4 >= 2) 3 else 2)
                mc.resizeDisplay()
                val family = if (scenario % 2 == 0) ComputerFamily.NORMAL else ComputerFamily.ADVANCED
                val inventory = Inventory(null)
                val terminal = NetworkedTerminal(51, 19, family == ComputerFamily.ADVANCED)
                var stack =
                    ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:computer_" + (if (family == ComputerFamily.NORMAL) "normal" else "advanced"))))
                // A custom host uses its own display item; automatic native-CC tabs must stay absent.
                if (scenario >= 4 && scenario < 8) stack = ItemStack(Items.STONE)
                if (scenario >= 8) stack.set<String>(de.teutonstudio.ccaeroworks.registry.CCDataComponents.DESK_ID.get(), "test-desk")
                val menu = ComputerMenuWithoutInventory(
                    ModRegistry.Menus.COMPUTER.get(),
                    7,
                    inventory,
                    ComputerContainerData(family, TerminalState.create(terminal), stack, 1024)
                )
                val screen: AbstractContainerScreen<*> = if (scenario < 4 || scenario >= 8)
                    ComputerScreen<ComputerMenuWithoutInventory>(
                        menu,
                        inventory,
                        Component.literal("TeutonLib UI verification")
                    )
                else
                    TemplateComputerScreen<ComputerMenuWithoutInventory>(
                        menu,
                        inventory,
                        Component.literal("Addon UI template verification")
                    )
                mc.setScreen(screen)
                verifyWorkspace(screen, scenario)
                stage++
            } else if (stage <= scenarioCount * 2) {
                Screenshot.grab(
                    mc.gameDirectory,
                    "teutonlib-computer-ui-" + stage + ".png",
                    mc.mainRenderTarget,
                    Consumer { message: Component? -> })
                finishWorkspace(mc)
                stage++
            } else {
                Files.createDirectories(Path.of("../../build/reports/smoke"))
                Files.writeString(
                    Path.of("../../build/reports/smoke/computer-ui.txt"),
                    "PASS: $scenarioCount scenarios; native CC and addon template (Aeroworks workspace when installed), normal/advanced, GUI scales 2/3, Standard/Sources/Channels tabs, editor focus, selected channel, source API open/scroll/close, naming, Escape, terminal focus and resize session renewal\n"
                )
                mc.stop()
                stage++
            }
        } catch (error: Throwable) {
            TeutonLib.Companion.LOGGER.error("Computer UI verification failed", error)
            mc.stop()
            stage = 100
        }
    }
}
