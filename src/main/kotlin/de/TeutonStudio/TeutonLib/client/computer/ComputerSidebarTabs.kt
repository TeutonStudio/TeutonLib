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
    @JvmStatic
    fun top(top: Int, sidebarOffset: Int, index: Int): Int {
        return top + sidebarOffset + ComputerSidebar.HEIGHT + 2 + index * (HEIGHT + 2)
    }

    @JvmStatic
    fun accent(family: ComputerFamily?): Int {
        return if (family == ComputerFamily.ADVANCED) -0x33ab else -0x2f2f30
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
        private val icon: ResourceLocation,
        private val hover: ResourceLocation,
        private val selected: BooleanSupplier,
        private val action: Runnable
    ) : AbstractWidget(x, y, AbstractComputerMenu.SIDEBAR_WIDTH, HEIGHT, label) {
        init {
            setTooltip(Tooltip.create(getMessage()))
        }

        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val casing = GuiSprites.getComputerTextures(family).sidebar()
            if (casing != null) g.blitSprite(casing, getX(), getY(), width, height)
            g.blitSprite(
                if (isHoveredOrFocused() || selected.asBoolean) hover else icon,
                getX() + 4,
                getY() + 5,
                12,
                12
            )
            if (selected.asBoolean) g.fill(
                getX() + width - 2,
                getY() + 5,
                getX() + width,
                getY() + height - 5,
                accent(family)
            )
        }

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
