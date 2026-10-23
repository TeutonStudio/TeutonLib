package de.TeutonStudio.TeutonLib.client.computer

import dan200.computercraft.client.gui.GuiSprites
import dan200.computercraft.client.gui.widgets.ComputerSidebar
import dan200.computercraft.shared.computer.core.ComputerFamily
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import java.util.function.BooleanSupplier
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/** Native CC casing and the same 12px symbols used by the Aeroworks desk sidebar.  */
object ComputerSidebarTabs {
    const val HEIGHT: Int = 22
    private val SOURCES_ICON = ResourceLocation.fromNamespaceAndPath("teutonlib", "buttons/computer_sources")
    private val CHANNELS_ICON = ResourceLocation.fromNamespaceAndPath("teutonlib", "buttons/computer_channels")

    @JvmStatic
    fun top(top: Int, sidebarOffset: Int, index: Int): Int {
        return top + sidebarOffset + ComputerSidebar.HEIGHT + 2 + index * (HEIGHT + 2)
    }

    @JvmStatic
    fun accent(family: ComputerFamily?): Int {
        return if (family == ComputerFamily.ADVANCED) -0x33ab else -0x2f2f30
    }

    @JvmStatic
    fun standardTab(
        x: Int,
        y: Int,
        family: ComputerFamily,
        selected: BooleanSupplier,
        action: Runnable
    ): Tab {
        return ComputerSidebarTabs.Tab(
            x,
            y,
            family,
            Component.translatable("teutonlib.computer.standard"),
            null,
            null,
            selected,
            action
        )
    }

    @JvmStatic
    fun tab(x: Int, y: Int, family: ComputerFamily, name: String, selected: BooleanSupplier, action: Runnable): Tab {
        return tab(
            x, y, family, Component.translatable("teutonlib.computer." + name),
            ResourceLocation.fromNamespaceAndPath("teutonlib", "buttons/computer_" + name),
            ResourceLocation.fromNamespaceAndPath("teutonlib", "buttons/computer_" + name + "_hover"), selected, action
        )
    }

    @JvmStatic
    fun tab(
        x: Int, y: Int, family: ComputerFamily, label: Component, icon: ResourceLocation,
        hover: ResourceLocation, selected: BooleanSupplier, action: Runnable
    ): Tab {
        return ComputerSidebarTabs.Tab(x, y, family, label, icon, hover, selected, action)
    }

    class Tab constructor(
        x: Int,
        y: Int,
        private val family: ComputerFamily,
        label: Component,
        private val icon: ResourceLocation?,
        private val hover: ResourceLocation?,
        private val selected: BooleanSupplier,
        private val action: Runnable
    ) : AbstractWidget(x, y, AbstractComputerMenu.SIDEBAR_WIDTH, HEIGHT, label) {
        init {
            setTooltip(Tooltip.create(getMessage()))
        }

        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val casing = GuiSprites.getComputerTextures(family).sidebar()
            if (casing != null) g.blitSprite(casing, getX(), getY(), width, height)
            when (icon) {
                null -> renderTerminalIcon(g)
                SOURCES_ICON -> renderFlowIcon(g, true)
                CHANNELS_ICON -> renderFlowIcon(g, false)
                else -> {
                    val sprite = if (isHoveredOrFocused() || selected.asBoolean) hover else icon
                    if (sprite == null) renderTerminalIcon(g) else {
                        g.blitSprite(sprite, getX() + 4, getY() + 5, 12, 12)
                    }
                }
            }
            if (selected.asBoolean) g.fill(
                getX() + width - 2,
                getY() + 5,
                getX() + width,
                getY() + height - 5,
                accent(family)
            )
        }

        private fun renderTerminalIcon(g: GuiGraphics) {
            val color = iconColor()
            val left = getX() + 4
            val top = getY() + 6
            renderScreenFrame(g, left, top, color)
            g.fill(left + 4, top + 4, left + 5, top + 5, color)
            g.fill(left + 5, top + 5, left + 6, top + 6, color)
            g.fill(left + 4, top + 6, left + 5, top + 7, color)
            g.fill(left + 6, top + 7, left + 9, top + 8, color)
        }

        private fun renderFlowIcon(g: GuiGraphics, incoming: Boolean) {
            val color = iconColor()
            val left = getX() + 4
            val top = getY() + 6
            renderScreenFrame(g, left, top, color)
            val tip = left + if (incoming) 7 else 11
            val tail = left + if (incoming) 0 else 5
            g.fill(tail, top + 5, tip + 1, top + 6, color)
            val head = tip - 2
            g.fill(head, top + 3, head + 1, top + 4, color)
            g.fill(head + 1, top + 4, head + 2, top + 5, color)
            g.fill(head + 1, top + 6, head + 2, top + 7, color)
            g.fill(head, top + 7, head + 1, top + 8, color)
        }

        private fun renderScreenFrame(g: GuiGraphics, left: Int, top: Int, color: Int) {
            g.fill(left + 2, top + 1, left + 10, top + 2, color)
            g.fill(left + 2, top + 2, left + 3, top + 10, color)
            g.fill(left + 9, top + 2, left + 10, top + 10, color)
            g.fill(left + 2, top + 9, left + 10, top + 10, color)
        }

        private fun iconColor(): Int = if (isHoveredOrFocused() || selected.asBoolean) -0x1 else -0x232324

        override fun onClick(x: Double, y: Double) {
            action.run()
        }

        override fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean {
            if (active && (key == 257 || key == 32 || key == 335)) {
                playDownSound(Minecraft.getInstance().soundManager)
                action.run()
                return true
            }
            return super.keyPressed(key, scan, modifiers)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            defaultButtonNarrationText(output)
        }
    }
}
