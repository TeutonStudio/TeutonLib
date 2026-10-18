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
    private fun verifyDesk(screen: AbstractContainerScreen<*>, scenario: Int) {
        val panels = (screen as WorkspacePanels.Owner).`teutonlib$workspace`()!!
        if (panels == null) throw AssertionError("Aeroworks workspace missing")
        val tabs: MutableList<ComputerSidebarTabs.Tab> =
            screen.children().stream().filter { c: GuiEventListener? -> c is ComputerSidebarTabs.Tab }
                .map<ComputerSidebarTabs.Tab> { c: GuiEventListener? -> c as ComputerSidebarTabs.Tab }.toList()
        if (tabs.size != 2) throw AssertionError("Duplicate Aeroworks sidebar: " + tabs.size)
        val page = if (scenario % 2 == 0) 2 else 1
        val tab = tabs.get(if (page == 1) 0 else 1)
        screen.mouseClicked((tab.x + 8).toDouble(), (tab.y + 10).toDouble(), 0)
        if (panels.page() != page) throw AssertionError("Aeroworks tab order/state")
        val rename = ComputerWorkspace.Action("rename", "Rename", true, false, false)
        val child = ComputerWorkspace.Entry(
            "wire:stable", "Engine", "12/15 · 2 connections", "", "wire:stable", true, false,
            mutableListOf<ComputerWorkspace.Entry>(), java.util.List.of<ComputerWorkspace.Action>(rename), CompoundTag()
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
        panels.receive(WorkspacePayloads.Response(7, panels.session(), de.teutonstudio.ccaeroworks.compat.teutonlib.DeskComputerWorkspace.ID, page, snapshot.encode()))
        val rows: AbstractWidget = screen.children().stream()
            .filter { c: GuiEventListener -> c.javaClass.name.endsWith("WorkspacePanels\$Rows") }
            .map<AbstractWidget> { c: GuiEventListener? -> c as AbstractWidget }.findFirst().orElseThrow()!!
        screen.mouseClicked((rows.x + 30).toDouble(), (rows.y + 52).toDouble(), 0)
        val terminal: TerminalWidget = screen.children().stream().filter { c: GuiEventListener? -> c is TerminalWidget }
            .map<TerminalWidget> { c: GuiEventListener? -> c as TerminalWidget }.findFirst().orElseThrow()!!
        if (terminal.active || terminal.visible) throw AssertionError("Terminal still accepts input under addon page")
        if (page == 2) {
            screen.keyPressed(342, 0, 0) // Host must apply the requested editor focus after a response.
            val field: EditBox = screen.children().stream().filter { c: GuiEventListener? -> c is EditBox }
                .map<EditBox> { c: GuiEventListener? -> c as EditBox }.findFirst().orElseThrow()!!
            if (screen.focused !== field || !field.isFocused()) throw AssertionError("Workspace name focus missing")
            screen.keyPressed(69, 0, 0)
            screen.charTyped('e', 0)
            if (field.value != "e" || Minecraft.getInstance().screen !== screen) throw AssertionError("Workspace inventory key interrupted editor")
        }
    }

    @JvmStatic
    private fun finishDesk(mc: Minecraft) {
        val screen = mc.screen
        var panels = (screen as WorkspacePanels.Owner).`teutonlib$workspace`()!!
        val page = panels.page()
        val session = panels.session()
        mc.resizeDisplay()
        panels = (screen as WorkspacePanels.Owner).`teutonlib$workspace`()!!
        if (panels.page() != page || session == panels.session()) throw AssertionError("Workspace resize state/session")
        screen.keyPressed(256, 0, 0)
        if (mc.screen !== screen || panels.page() != 0) throw AssertionError("Workspace Escape closed screen")
        if (screen.focused !is TerminalWidget) throw AssertionError("Workspace terminal focus not restored")
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
        (mc.screen as? ComputerPanels.Owner)?.`teutonlib$panels`()?.tick()
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
                if (scenario >= 8) {
                    verifyDesk(screen, scenario)
                    stage++
                    return
                }
                val panels = (screen as Any as ComputerPanels.Owner).`teutonlib$panels`()!!
                if (panels == null) throw AssertionError("CC tabs missing")
                // Exercise the actual sidebar button so terminal visibility follows page state.
                val tabs: MutableList<ComputerSidebarTabs.Tab> =
                    screen.children().stream().filter { c: GuiEventListener? -> c is ComputerSidebarTabs.Tab }
                        .map<ComputerSidebarTabs.Tab> { c: GuiEventListener? -> c as ComputerSidebarTabs.Tab }.toList()
                if (tabs.size != 2) throw AssertionError("Expected exactly two shared tabs, got " + tabs.size)
                val tab = tabs.get(if (scenario % 2 == 0) 1 else 0)
                screen.mouseClicked((tab.x + 8).toDouble(), (tab.y + 10).toDouble(), 0)
                if (panels.page() != (if (scenario % 2 == 0) 2 else 1)) throw AssertionError("Sidebar icon did not switch pages")
                val snapshot = CompoundTag()
                snapshot.putBoolean("dbw", true)
                val channels = ListTag()
                for (i in 0..7) {
                    val t = CompoundTag()
                    t.putString("name", if (i == 0) "engine" else "channel_" + i)
                    t.putString("direction", if (i % 2 == 0) "output" else "input")
                    t.putString("side", if (i % 2 == 0) "" else "top")
                    t.putBoolean("public", i % 2 == 0)
                    t.putInt("value", 12)
                    t.putInt("connections", 2)
                    t.putBoolean("editable", true)
                    channels.add(t)
                }
                snapshot.put("channels", channels)
                val sources = ListTag()
                for (connection in arrayOf<String>("local", "wired", "wireless")) {
                    val t = CompoundTag()
                    t.putString(
                        "name",
                        if (connection == "wireless") "cc_aeroworks:link_890e0b6f-4fd0-4aeb-bfcc-a13474c4e716" else if (connection == "local") "left" else "inventory_5"
                    )
                    t.putString("types", "inventory, fluid_storage")
                    t.putString("connection", connection)
                    t.putString("position", "12, 200, 8")
                    if (connection == "wireless") t.putString("link", "12, 201, 8")
                    sources.add(t)
                }
                snapshot.put("sources", sources)
                panels.receive(ComputerPayloads.Snapshot(7, panels.session(), snapshot))
                val workspace: AbstractWidget = screen.children().stream().filter { w: GuiEventListener? ->
                    w is AbstractWidget && w.javaClass.name.endsWith("ComputerPanels\$Rows")
                }.map<AbstractWidget> { w: GuiEventListener? -> w as AbstractWidget }.findFirst().orElseThrow()!!
                if (scenario % 2 == 0) {
                    val create: Button = screen.children().stream().filter { w: GuiEventListener? ->
                        w is net.minecraft.client.gui.components.Button && w.message
                            .string == "+" && w.y == workspace.y + 3
                    }
                        .map<net.minecraft.client.gui.components.Button> { w: GuiEventListener? -> w as net.minecraft.client.gui.components.Button }
                        .findFirst().orElseThrow()!!
                    screen.mouseClicked((create.x + 5).toDouble(), (create.y + 5).toDouble(), 0)
                    if (screen.focused !is EditBox) throw AssertionError("New channel did not focus name field")
                }
                if (scenario % 4 == 2) screen.mouseClicked(
                    (workspace.x + 30).toDouble(),
                    (workspace.y + 49).toDouble(),
                    0
                )
                if (scenario % 4 == 3) screen.mouseClicked(
                    (workspace.x + 30).toDouble(),
                    (workspace.y + 130).toDouble(),
                    0
                )
                if (scenario % 2 == 0) {
                    val field: EditBox = screen.children().stream().filter { w: GuiEventListener? -> w is EditBox }
                        .map<EditBox> { w: GuiEventListener? -> w as EditBox }.findFirst().orElseThrow()!!
                    val previousName = field.value
                    field.setValue("")
                    screen.setFocused(field)
                    field.setFocused(true)
                    screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0)
                    screen.charTyped('e', 0)
                    if (mc.screen !== screen || field.value != "e") throw AssertionError("Inventory key interrupted channel naming")
                    field.setValue(if (scenario == 0) "engine_start" else previousName)
                    field.setFocused(false)
                    screen.setFocused(null)
                }
                stage++
            } else if (stage <= scenarioCount * 2) {
                Screenshot.grab(
                    mc.gameDirectory,
                    "teutonlib-computer-ui-" + stage + ".png",
                    mc.mainRenderTarget,
                    Consumer { message: Component? -> })
                if (stage >= 18) {
                    finishDesk(mc)
                    stage++
                    return
                }
                // Escape must return to the terminal without closing the computer.
                val screen = mc.screen
                screen!!.keyPressed(256, 0, 0)
                if (mc.screen !== screen || (screen as Any as ComputerPanels.Owner).`teutonlib$panels`()!!
                        .page() != 0
                ) throw AssertionError("Escape did not return to terminal")
                val tab: ComputerSidebarTabs.Tab =
                    screen.children().stream().filter { w: GuiEventListener? -> w is ComputerSidebarTabs.Tab }
                        .map<ComputerSidebarTabs.Tab> { w: GuiEventListener? -> w as ComputerSidebarTabs.Tab }
                        .findFirst()
                        .orElseThrow()!!
                screen.mouseClicked((tab.x + 8).toDouble(), (tab.y + 10).toDouble(), 0)
                screen.mouseClicked((tab.x + 8).toDouble(), (tab.y + 10).toDouble(), 0)
                if ((screen as Any as ComputerPanels.Owner).`teutonlib$panels`()!!
                        .page() != 0 || screen.focused !is TerminalWidget
                ) throw AssertionError("Tab toggle did not restore terminal focus")
                val oldPanels = (screen as Any as ComputerPanels.Owner).`teutonlib$panels`()!!
                mc.resizeDisplay()
                val rebuilt = (screen as Any as ComputerPanels.Owner).`teutonlib$panels`()!!
                if (oldPanels.session() == rebuilt.session()) throw AssertionError("Resize reused response session")
                if (rebuilt.page() != 0) throw AssertionError("Resize did not preserve terminal page")
                stage++
            } else {
                Files.createDirectories(Path.of("../../build/reports/smoke"))
                Files.writeString(
                    Path.of("../../build/reports/smoke/computer-ui.txt"),
                    "PASS: $scenarioCount scenarios; native CC and addon template (Aeroworks workspace when installed), normal/advanced, GUI scales 2/3, exactly two icon tabs, editor focus, selected channel, expanded source, naming, Escape, terminal focus and resize session renewal\n"
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
