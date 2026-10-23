package de.TeutonStudio.TeutonLib.client

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinks
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent

/** The subscriber itself has no optional Create, Ponder or CC types in its signature.  */
@EventBusSubscriber(modid = TeutonLib.Companion.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object PeripheralLinkClient {
    @SubscribeEvent
    fun setup(event: FMLClientSetupEvent) {
        if (ModList.get().isLoaded("create") && ModList.get()
                .isLoaded("computercraft")
        ) event.enqueueWork(Runnable {
            PeripheralLinkPonder.Companion.register()
        })
    }

    @SubscribeEvent
    fun screens(event: RegisterMenuScreensEvent) {
        if (ModList.get().isLoaded("create") && ModList.get().isLoaded("computercraft"))
            event.register(PeripheralLinks.MENU.get(), ::PeripheralLinkScreen)
    }
}
