package de.TeutonStudio.TeutonLib.client

import dan200.computercraft.client.gui.GuiSprites
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/** CC's native grey computer bezel around a terminal palette. */
internal object PeripheralLinkTheme {
    const val BACKGROUND = 0xff111111.toInt()
    const val PANEL = 0xff191919.toInt()
    const val HOVER = 0xff333333.toInt()
    const val TEXT = 0xfff0f0f0.toInt()
    const val MUTED = 0xffaaaaaa.toInt()
    const val ACCENT = 0xfff2b233.toInt()
    const val LINE = 0xff4c4c4c.toInt()

    fun frame(g: GuiGraphics, x: Int, y: Int, width: Int, height: Int, title: Component) {
        g.fill(x + 8, y + 8, x + width - 8, y + height - 8, BACKGROUND)
        g.blitSprite(GuiSprites.COMPUTER_NORMAL.border(), x, y, width, height)
        g.drawString(Minecraft.getInstance().font, title, x + 15, y + 14, ACCENT, false)
        g.fill(x + 14, y + 28, x + width - 14, y + 29, LINE)
    }
    fun button(x: Int, y: Int, width: Int, height: Int, text: Component,
               selected: () -> Boolean = { false }, action: Button.OnPress): Button =
        TerminalButton(x, y, width, height, text, selected, action)

    private class TerminalButton(x: Int, y: Int, width: Int, height: Int, text: Component,
                                 private val selected: () -> Boolean, action: OnPress) :
        Button(x, y, width, height, text, action, DEFAULT_NARRATION) {
        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val chosen = selected()
            val border = if (isHoveredOrFocused && active || chosen) ACCENT else LINE
            g.fill(x, y, x + width, y + height, border)
            g.fill(x + 1, y + 1, x + width - 1, y + height - 1, if (active && isHoveredOrFocused) HOVER else PANEL)
            val font = Minecraft.getInstance().font
            val text = font.plainSubstrByWidth(message.string, width - 8)
            g.drawString(font, text, x + (width - font.width(text)) / 2, y + (height - font.lineHeight) / 2,
                if (chosen) ACCENT else if (active) TEXT else MUTED, false)
        }
    }
}
