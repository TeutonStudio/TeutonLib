package de.TeutonStudio.TeutonLib.client;

import de.TeutonStudio.TeutonLib.TeutonLib;
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class PeripheralLinkPonder implements PonderPlugin {
    public static void register() { PonderIndex.addPlugin(new PeripheralLinkPonder()); }
    @Override public String getModId() { return TeutonLib.MOD_ID; }
    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "peripheral_link"))
                .addStoryBoard("peripheral_link", PeripheralLinkPonder::scene);
    }
    private static void scene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("peripheral_link", I18n.get("teutonlib.ponder.peripheral_link.title"));
        scene.configureBasePlate(0, 0, 5); scene.showBasePlate();
        BlockPos computer = util.grid().at(1, 1, 2), chest = util.grid().at(3, 1, 2), link = chest.above();
        scene.world().setBlock(computer, dan200.computercraft.shared.ModRegistry.Blocks.COMPUTER_NORMAL.get().defaultBlockState(), false);
        scene.world().setBlock(chest, Blocks.CHEST.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(computer), Pointing.DOWN, 60).rightClick().withItem(new ItemStack(PeripheralLinks.ITEM.get()));
        scene.overlay().showText(70).text(I18n.get("teutonlib.ponder.peripheral_link.select")).pointAt(util.vector().centerOf(computer)).placeNearTarget();
        scene.idle(80);
        scene.world().setBlock(link, PeripheralLinks.BLOCK.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(link), Direction.DOWN);
        scene.overlay().showText(80).text(I18n.get("teutonlib.ponder.peripheral_link.place")).pointAt(util.vector().centerOf(link)).placeNearTarget();
        scene.idle(90);
        scene.overlay().showText(90).text(I18n.get("teutonlib.ponder.peripheral_link.lua")).pointAt(util.vector().centerOf(computer)).placeNearTarget();
        scene.idle(100);
        scene.overlay().showText(90).text(I18n.get("teutonlib.ponder.peripheral_link.range")).pointAt(util.vector().centerOf(link)).placeNearTarget();
        scene.idle(100); scene.markAsFinished();
    }
}
