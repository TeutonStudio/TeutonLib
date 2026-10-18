package de.TeutonStudio.TeutonLib.mixin.computer.client;

import dan200.computercraft.client.gui.AbstractComputerScreen;
import dan200.computercraft.client.gui.widgets.TerminalWidget;
import dan200.computercraft.client.gui.widgets.ComputerSidebar;
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu;
import de.TeutonStudio.TeutonLib.client.computer.ComputerPanels;
import de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=AbstractComputerScreen.class,remap=false)
public abstract class ComputerScreenMixin extends AbstractContainerScreen<AbstractComputerMenu> implements ComputerPanels.Owner {
    @Shadow protected TerminalWidget terminal;
    @Shadow @Final protected int sidebarYOffset;
    @Shadow @Final protected ComputerFamily family;
    @Unique private ComputerPanels teutonlib$panels;
    protected ComputerScreenMixin(AbstractComputerMenu menu,Inventory inventory,Component title) { super(menu,inventory,title); }
    @Override public ComputerPanels teutonlib$panels() { return teutonlib$panels; }
    @Inject(method="init()V",at=@At("TAIL"))
    private void teutonlib$init(CallbackInfo ci) {
        var deskComponent = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cc_aeroworks", "desk_id"));
        if (deskComponent != null && menu.getDisplayStack().has(deskComponent)) return;
        if(terminal==null || !(menu.getDisplayStack().getItem() instanceof net.minecraft.world.item.BlockItem block)
            || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).getNamespace().equals("computercraft")
            || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath().startsWith("computer_")) return;
        int page=teutonlib$panels==null?0:teutonlib$panels.page();
        teutonlib$panels=new ComputerPanels(menu.containerId,terminal.getX(),terminal.getY(),terminal.getWidth(),terminal.getHeight(),font,
            w -> addRenderableWidget(w), () -> setFocused(terminal));
        teutonlib$panels.family(family);
        for(int i=1;i<=2;i++) {
            int target=i;
            addRenderableWidget(ComputerSidebarTabs.tab(leftPos, ComputerSidebarTabs.top(topPos, sidebarYOffset, i-1), family,
                i==1?"sources":"channels", () -> teutonlib$panels.page()==target,
                () -> teutonlib$show(teutonlib$panels.page()==target?0:target)));
        }
        teutonlib$show(page);
    }
    @Unique private void teutonlib$show(int page) {
        teutonlib$panels.show(page); terminal.visible=page==0; terminal.active=page==0;
        if(page!=0) setFocused(null);
    }
    @Unique private void teutonlib$focus() {
        if(teutonlib$panels==null) return;
        var requested=teutonlib$panels.takeRequestedFocus();
        if(requested!=null) { setFocused(requested); requested.setFocused(true); }
        else if(getFocused() instanceof ComputerSidebarTabs.Tab) setFocused(teutonlib$panels.page()==0?terminal:null);
    }
    @Inject(method="mouseClicked(DDI)Z",at=@At("TAIL"))
    private void teutonlib$clickFocus(double x,double y,int button,CallbackInfoReturnable<Boolean> cir) { teutonlib$focus(); }
    @Inject(method="keyPressed(III)Z",at=@At("TAIL"))
    private void teutonlib$keyFocus(int key,int scan,int mods,CallbackInfoReturnable<Boolean> cir) { teutonlib$focus(); }
    @Inject(method="containerTick()V",at=@At("TAIL"))
    private void teutonlib$tick(CallbackInfo ci) { if(teutonlib$panels!=null) teutonlib$panels.tick(); }
    @Inject(method="keyPressed(III)Z",at=@At("HEAD"),cancellable=true)
    private void teutonlib$keys(int key,int scan,int mods,CallbackInfoReturnable<Boolean> cir) {
        if(teutonlib$panels==null || teutonlib$panels.page()==0) return;
        if(key==256) { teutonlib$show(0); cir.setReturnValue(true); }
        else if(getFocused() instanceof net.minecraft.client.gui.components.EditBox && minecraft.options.keyInventory.matches(key,scan))
            cir.setReturnValue(true);
    }
}
