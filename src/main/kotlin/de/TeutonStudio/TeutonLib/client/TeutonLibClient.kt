package de.TeutonStudio.TeutonLib.client

import de.TeutonStudio.TeutonLib.TeutonLib
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

/** NeoForge's built-in editor supports our ModConfigSpec without another GUI mod. */
@Mod(value = TeutonLib.MOD_ID, dist = [Dist.CLIENT])
class TeutonLibClient(modBus: IEventBus, container: ModContainer) {
    init {
        if (de.TeutonStudio.TeutonLib.integration.HosePulleyCompatibility.active()) {
            de.TeutonStudio.TeutonLib.network.HoseCablePayload.receiver = de.TeutonStudio.TeutonLib.client.hose.HoseCableClient::apply
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(de.TeutonStudio.TeutonLib.client.hose.HoseCableClient)
        }
        if (de.TeutonStudio.TeutonLib.integration.HybridTrainCompatibility.active()) {
            modBus.addListener<net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent>(
                de.TeutonStudio.TeutonLib.client.train.HybridTrainClient::creative)
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener<net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut> {
                de.TeutonStudio.TeutonLib.client.train.HybridTrainClient.clear()
            }
        }
        container.registerExtensionPoint(
            IConfigScreenFactory::class.java,
            IConfigScreenFactory { mod, parent -> ConfigurationScreen(mod, parent) }
        )
    }
}
