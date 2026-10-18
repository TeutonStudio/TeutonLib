package de.TeutonStudio.TeutonLib.client

import de.TeutonStudio.TeutonLib.TeutonLib
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent

/** The subscriber itself has no optional Create, Ponder or CC types in its signature.  */
@EventBusSubscriber(modid = TeutonLib.Companion.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object PeripheralLinkClient {
    @SubscribeEvent
    fun setup(event: FMLClientSetupEvent) {
        if (ModList.get().isLoaded("create") && ModList.get()
                .isLoaded("computercraft")
        ) event.enqueueWork(Runnable { PeripheralLinkPonder.Companion.register() })
    }
}
