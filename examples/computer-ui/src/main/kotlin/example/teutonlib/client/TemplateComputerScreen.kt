package example.teutonlib.client

import dan200.computercraft.client.gui.AbstractComputerScreen
import dan200.computercraft.client.gui.GuiSprites
import dan200.computercraft.client.gui.widgets.ComputerSidebar
import dan200.computercraft.client.gui.widgets.TerminalWidget
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.TeutonStudio.TeutonLib.ClientConfig
import de.TeutonStudio.TeutonLib.client.computer.ComputerPanels
import de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.lwjgl.glfw.GLFW

/** Copy into your addon's client package. The menu must use your own display item.  */
class TemplateComputerScreen<T : AbstractComputerMenu>
    (menu: T, inventory: Inventory, title: Component) : AbstractComputerScreen<T>(menu, inventory, title, 12),
    ComputerPanels.Owner {
    private var panels: ComputerPanels? = null

    init {
        imageWidth = TerminalWidget.getWidth(terminalData.width) + 24 + AbstractComputerMenu.SIDEBAR_WIDTH
        imageHeight = TerminalWidget.getHeight(terminalData.height) + 24
    }

    override fun createTerminal(): TerminalWidget {
        return TerminalWidget(
            terminalData, computerInput, computerActions,
            leftPos + AbstractComputerMenu.SIDEBAR_WIDTH + 12, topPos + 12
        )
    }

    override fun init() {
        val previousPage = if (panels == null) 0 else panels!!.page()
        super.init()
        if (!ClientConfig.COMPUTER_UI_ENABLED.get()) {
            panels = null
            return
        }
        // Rebuild after resize. A fresh controller gets a fresh response session ID.
        panels = ComputerPanels(
            menu.containerId,
            terminal!!.x,
            terminal!!.y,
            terminal!!.width,
            terminal!!.height,
            font,
            Consumer { widget: AbstractWidget? -> addRenderableWidget<AbstractWidget>(widget) },
            Runnable { setFocused(terminal) })
        panels!!.family(family)
        for (page in 1..2) {
            val target = page
            addRenderableWidget<ComputerSidebarTabs.Tab>(
                ComputerSidebarTabs.tab(
                    leftPos,
                    ComputerSidebarTabs.top(topPos, sidebarYOffset, page - 1), family,
                    if (page == 1) "sources" else "channels", BooleanSupplier { panels!!.page() == target },
                    Runnable { showPage(if (panels!!.page() == target) 0 else target) })
            )
        }
        showPage(previousPage)
    }

    private fun showPage(page: Int) {
        panels!!.show(page)
        terminal!!.visible = page == 0
        terminal!!.active = page == 0
        setFocused(if (page == 0) terminal else null)
    }

    private fun applyRequestedFocus() {
        if (panels == null) return
        val requested = panels!!.takeRequestedFocus()
        if (requested != null) {
            setFocused(requested)
            requested.setFocused(true)
        } else if (getFocused() is ComputerSidebarTabs.Tab) {
            setFocused(if (panels!!.page() == 0) terminal else null)
        }
    }

    override fun mouseClicked(x: Double, y: Double, button: Int): Boolean {
        val handled = super.mouseClicked(x, y, button)
        applyRequestedFocus()
        return handled
    }

    override fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean {
        if (panels != null && panels!!.page() != 0) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                showPage(0)
                return true
            }
            // Let charTyped deliver inventory-key characters to the name field.
            if (getFocused() is EditBox && minecraft!!.options.keyInventory.matches(key, scan)) return true
        }
        val handled = super.keyPressed(key, scan, modifiers)
        applyRequestedFocus()
        return handled
    }

    override fun containerTick() {
        super.containerTick()
        if (panels != null) panels!!.tick()
    }

    override fun `teutonlib$panels`(): ComputerPanels? {
        return panels
    }

    override fun renderBg(graphics: GuiGraphics, delta: Float, mouseX: Int, mouseY: Int) {
        val textures = GuiSprites.getComputerTextures(family)
        graphics.blitSprite(
            textures.border(), terminal!!.x - 12, terminal!!.y - 12,
            terminal!!.width + 24, terminal!!.height + 24
        )
        if (textures.sidebar() != null) graphics.blitSprite(
            textures.sidebar(), leftPos,
            topPos + sidebarYOffset, AbstractComputerMenu.SIDEBAR_WIDTH, ComputerSidebar.HEIGHT
        )
    }
}
