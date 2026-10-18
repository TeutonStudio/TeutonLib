package de.TeutonStudio.TeutonLib.client;

import de.TeutonStudio.TeutonLib.TeutonLib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** The subscriber itself has no optional Create, Ponder or CC types in its signature. */
@EventBusSubscriber(modid = TeutonLib.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PeripheralLinkClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("create") && ModList.get().isLoaded("computercraft"))
            event.enqueueWork(PeripheralLinkPonder::register);
    }
    private PeripheralLinkClient() {}
}
