package de.TeutonStudio.TeutonLib.peripheraltest;

import de.TeutonStudio.TeutonLib.client.computer.ComputerPanels;
import de.TeutonStudio.TeutonLib.integration.computer.ComputerPayloads;
import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory;
import dan200.computercraft.shared.computer.terminal.*;
import dan200.computercraft.shared.network.container.ComputerContainerData;
import dan200.computercraft.client.gui.ComputerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in renderer/focus verification. Does not connect to or alter a real world. */
@EventBusSubscriber(modid="teutonlib_peripheral_test",value=Dist.CLIENT)
public final class ComputerUiVerification {
    private static int stage, ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("teutonlib.computerUiTests")) return;
        var mc=Minecraft.getInstance();
        if(stage==0) {
            if(mc.screen==null || mc.getOverlay()!=null) return;
            mc.getWindow().setWindowed(1280,800); stage=1; ticks=0;
            try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("../../build/reports/smoke/computer-ui.txt")); } catch(java.io.IOException e) { throw new RuntimeException(e); }
        }
        if(mc.screen instanceof ComputerPanels.Owner owner && owner.teutonlib$panels()!=null) owner.teutonlib$panels().tick();
        if(++ticks<40) return;
        ticks=0;
        try {
            if(stage%2==1 && stage<=15) {
                int scenario=(stage-1)/2;
                mc.options.guiScale().set(scenario%4>=2?3:2); mc.resizeDisplay();
                var family=scenario%2==0?ComputerFamily.NORMAL:ComputerFamily.ADVANCED;
                var inventory=new Inventory(null);
                var terminal=new NetworkedTerminal(51,19,family==ComputerFamily.ADVANCED);
                var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:computer_"+(family==ComputerFamily.NORMAL?"normal":"advanced"))));
                // A custom host uses its own display item; automatic native-CC tabs must stay absent.
                if(scenario>=4) stack=new ItemStack(net.minecraft.world.item.Items.STONE);
                var menu=new ComputerMenuWithoutInventory(ModRegistry.Menus.COMPUTER.get(),7,inventory,new ComputerContainerData(family,TerminalState.create(terminal),stack,1024));
                net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen=scenario<4
                    ? new ComputerScreen<>(menu,inventory,Component.literal("TeutonLib UI verification"))
                    : new example.teutonlib.client.TemplateComputerScreen<>(menu,inventory,Component.literal("Addon UI template verification"));
                mc.setScreen(screen);
                var panels=((ComputerPanels.Owner)(Object)screen).teutonlib$panels();
                if(panels==null) throw new AssertionError("CC tabs missing");
                // Exercise the actual sidebar button so terminal visibility follows page state.
                var tabs=screen.children().stream().filter(c -> c instanceof de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs.Tab).map(c -> (de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs.Tab)c).toList();
                if(tabs.size()!=2) throw new AssertionError("Expected exactly two shared tabs, got "+tabs.size());
                var tab=tabs.get(scenario%2==0?1:0);
                screen.mouseClicked(tab.getX()+8,tab.getY()+10,0);
                if(panels.page()!=(scenario%2==0?2:1)) throw new AssertionError("Sidebar icon did not switch pages");
                CompoundTag snapshot=new CompoundTag(); snapshot.putBoolean("dbw",true);
                ListTag channels=new ListTag();
                for(int i=0;i<8;i++) { var t=new CompoundTag(); t.putString("name",i==0?"engine":"channel_"+i); t.putString("direction",i%2==0?"output":"input"); t.putString("side",i%2==0?"":"top"); t.putBoolean("public",i%2==0); t.putInt("value",12); t.putInt("connections",2); t.putBoolean("editable",true); channels.add(t); }
                snapshot.put("channels",channels);
                ListTag sources=new ListTag(); for(String connection:new String[]{"local","wired","wireless"}) {
                    var t=new CompoundTag(); t.putString("name",connection.equals("wireless")?"cc_aeroworks:link_890e0b6f-4fd0-4aeb-bfcc-a13474c4e716":connection.equals("local")?"left":"inventory_5");
                    t.putString("types","inventory, fluid_storage"); t.putString("connection",connection); t.putString("position","12, 200, 8");
                    if(connection.equals("wireless"))t.putString("link","12, 201, 8"); sources.add(t);
                } snapshot.put("sources",sources);
                panels.receive(new ComputerPayloads.Snapshot(7,panels.session(),snapshot));
                var workspace=screen.children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.AbstractWidget && w.getClass().getName().endsWith("ComputerPanels$Rows")).map(w -> (net.minecraft.client.gui.components.AbstractWidget)w).findFirst().orElseThrow();
                if(scenario%2==0) {
                    var create=screen.children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("+") && b.getY()==workspace.getY()+3).map(w -> (net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow();
                    screen.mouseClicked(create.getX()+5,create.getY()+5,0);
                    if(!(screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox)) throw new AssertionError("New channel did not focus name field");
                }
                if(scenario%4==2) screen.mouseClicked(workspace.getX()+30,workspace.getY()+49,0);
                if(scenario%4==3) screen.mouseClicked(workspace.getX()+30,workspace.getY()+130,0);
                if(scenario%2==0) {
                    var field=screen.children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.EditBox).map(w -> (net.minecraft.client.gui.components.EditBox)w).findFirst().orElseThrow();
                    String previousName=field.getValue(); field.setValue("");
                    screen.setFocused(field); field.setFocused(true);
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_E,0,0); screen.charTyped('e',0);
                    if(mc.screen!=screen || !field.getValue().equals("e")) throw new AssertionError("Inventory key interrupted channel naming");
                    field.setValue(scenario==0?"engine_start":previousName); field.setFocused(false); screen.setFocused(null);
                }
                stage++;
            } else if(stage<=16) {
                Screenshot.grab(mc.gameDirectory,"teutonlib-computer-ui-"+stage+".png",mc.getMainRenderTarget(),message -> {});
                // Escape must return to the terminal without closing the computer.
                var screen=mc.screen; screen.keyPressed(256,0,0);
                if(mc.screen!=screen || ((ComputerPanels.Owner)(Object)screen).teutonlib$panels().page()!=0) throw new AssertionError("Escape did not return to terminal");
                var tab=screen.children().stream().filter(w -> w instanceof de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs.Tab).map(w -> (de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs.Tab)w).findFirst().orElseThrow();
                screen.mouseClicked(tab.getX()+8,tab.getY()+10,0); screen.mouseClicked(tab.getX()+8,tab.getY()+10,0);
                if(((ComputerPanels.Owner)(Object)screen).teutonlib$panels().page()!=0 || !(screen.getFocused() instanceof dan200.computercraft.client.gui.widgets.TerminalWidget)) throw new AssertionError("Tab toggle did not restore terminal focus");
                var oldPanels=((ComputerPanels.Owner)(Object)screen).teutonlib$panels();
                mc.resizeDisplay();
                var rebuilt=((ComputerPanels.Owner)(Object)screen).teutonlib$panels();
                if(oldPanels.session().equals(rebuilt.session())) throw new AssertionError("Resize reused response session");
                if(rebuilt.page()!=0) throw new AssertionError("Resize did not preserve terminal page");
                stage++;
            } else { java.nio.file.Files.createDirectories(java.nio.file.Path.of("../../build/reports/smoke"));
                java.nio.file.Files.writeString(java.nio.file.Path.of("../../build/reports/smoke/computer-ui.txt"),"PASS: native CC and addon template, normal/advanced, GUI scales 2/3, exactly two icon tabs, editor focus, selected channel, expanded source, naming, Escape, terminal focus and resize session renewal\n"); mc.stop(); stage++; }
        } catch(Throwable error) { de.TeutonStudio.TeutonLib.TeutonLib.LOGGER.error("Computer UI verification failed",error); mc.stop(); stage=100; }
    }
}
