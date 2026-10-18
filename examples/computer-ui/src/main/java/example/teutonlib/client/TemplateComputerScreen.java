package example.teutonlib.client;

import dan200.computercraft.client.gui.AbstractComputerScreen;
import dan200.computercraft.client.gui.GuiSprites;
import dan200.computercraft.client.gui.widgets.ComputerSidebar;
import dan200.computercraft.client.gui.widgets.TerminalWidget;
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu;
import de.TeutonStudio.TeutonLib.client.computer.ComputerPanels;
import de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/** Copy into your addon's client package. The menu must use your own display item. */
public final class TemplateComputerScreen<T extends AbstractComputerMenu>
        extends AbstractComputerScreen<T> implements ComputerPanels.Owner {
    private ComputerPanels panels;

    public TemplateComputerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 12);
        imageWidth = TerminalWidget.getWidth(terminalData.getWidth()) + 24 + AbstractComputerMenu.SIDEBAR_WIDTH;
        imageHeight = TerminalWidget.getHeight(terminalData.getHeight()) + 24;
    }

    @Override protected TerminalWidget createTerminal() {
        return new TerminalWidget(terminalData, computerInput, computerActions,
                leftPos + AbstractComputerMenu.SIDEBAR_WIDTH + 12, topPos + 12);
    }

    @Override protected void init() {
        int previousPage = panels == null ? 0 : panels.page();
        super.init();
        // Rebuild after resize. A fresh controller gets a fresh response session ID.
        panels = new ComputerPanels(menu.containerId, terminal.getX(), terminal.getY(),
                terminal.getWidth(), terminal.getHeight(), font,
                widget -> addRenderableWidget(widget), () -> setFocused(terminal));
        panels.family(family);
        for (int page = 1; page <= 2; page++) {
            int target = page;
            addRenderableWidget(ComputerSidebarTabs.tab(leftPos,
                    ComputerSidebarTabs.top(topPos, sidebarYOffset, page - 1), family,
                    page == 1 ? "sources" : "channels", () -> panels.page() == target,
                    () -> showPage(panels.page() == target ? 0 : target)));
        }
        showPage(previousPage);
    }

    private void showPage(int page) {
        panels.show(page);
        terminal.visible = page == 0;
        terminal.active = page == 0;
        setFocused(page == 0 ? terminal : null);
    }

    private void applyRequestedFocus() {
        var requested = panels.takeRequestedFocus();
        if (requested != null) {
            setFocused(requested);
            requested.setFocused(true);
        } else if (getFocused() instanceof ComputerSidebarTabs.Tab) {
            setFocused(panels.page() == 0 ? terminal : null);
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        applyRequestedFocus();
        return handled;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (panels.page() != 0) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { showPage(0); return true; }
            // Let charTyped deliver inventory-key characters to the name field.
            if (getFocused() instanceof EditBox && minecraft.options.keyInventory.matches(key, scan)) return true;
        }
        boolean handled = super.keyPressed(key, scan, modifiers);
        applyRequestedFocus();
        return handled;
    }

    @Override public void containerTick() { super.containerTick(); panels.tick(); }
    @Override public ComputerPanels teutonlib$panels() { return panels; }

    @Override protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        var textures = GuiSprites.getComputerTextures(family);
        graphics.blitSprite(textures.border(), terminal.getX() - 12, terminal.getY() - 12,
                terminal.getWidth() + 24, terminal.getHeight() + 24);
        if (textures.sidebar() != null) graphics.blitSprite(textures.sidebar(), leftPos,
                topPos + sidebarYOffset, AbstractComputerMenu.SIDEBAR_WIDTH, ComputerSidebar.HEIGHT);
    }
}
