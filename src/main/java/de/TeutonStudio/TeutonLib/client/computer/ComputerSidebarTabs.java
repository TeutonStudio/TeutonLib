package de.TeutonStudio.TeutonLib.client.computer;

import dan200.computercraft.client.gui.GuiSprites;
import dan200.computercraft.client.gui.widgets.ComputerSidebar;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.function.BooleanSupplier;

/** Native CC casing and the same 12px symbols used by the Aeroworks desk sidebar. */
public final class ComputerSidebarTabs {
    public static final int HEIGHT = 22;
    public static int top(int top, int sidebarOffset, int index) {
        return top + sidebarOffset + ComputerSidebar.HEIGHT + 2 + index * (HEIGHT + 2);
    }
    public static int accent(ComputerFamily family) { return family == ComputerFamily.ADVANCED ? 0xffffcc55 : 0xffd0d0d0; }
    public static Tab tab(int x, int y, ComputerFamily family, String name, BooleanSupplier selected, Runnable action) {
        return new Tab(x, y, family, name, selected, action);
    }
    public static final class Tab extends AbstractWidget {
        private final ComputerFamily family;
        private final ResourceLocation icon, hover;
        private final BooleanSupplier selected;
        private final Runnable action;
        private Tab(int x, int y, ComputerFamily family, String name, BooleanSupplier selected, Runnable action) {
            super(x, y, AbstractComputerMenu.SIDEBAR_WIDTH, HEIGHT, Component.translatable("teutonlib.computer." + name));
            this.family = family; this.selected = selected; this.action = action;
            icon = ResourceLocation.fromNamespaceAndPath("teutonlib", "buttons/computer_" + name);
            hover = ResourceLocation.fromNamespaceAndPath("teutonlib", "buttons/computer_" + name + "_hover");
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            var casing = GuiSprites.getComputerTextures(family).sidebar();
            if (casing != null) g.blitSprite(casing, getX(), getY(), width, height);
            g.blitSprite(isHoveredOrFocused() || selected.getAsBoolean() ? hover : icon, getX() + 4, getY() + 5, 12, 12);
            if (selected.getAsBoolean()) g.fill(getX() + width - 2, getY() + 5, getX() + width, getY() + height - 5, accent(family));
        }
        @Override public void onClick(double x, double y) { action.run(); }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (active && (key == 257 || key == 32 || key == 335)) { playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager()); action.run(); return true; }
            return super.keyPressed(key, scan, modifiers);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
    private ComputerSidebarTabs() {}
}
