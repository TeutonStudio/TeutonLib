package de.TeutonStudio.TeutonLib.client

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.compat.CompatibilityDiagnostics
import de.TeutonStudio.TeutonLib.compat.MobileEnvironment
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import org.lwjgl.opengl.GL11

/** NeoForge's built-in editor supports our ModConfigSpec without another GUI mod. */
@Mod(value = TeutonLib.MOD_ID, dist = [Dist.CLIENT])
class TeutonLibClient(modBus: IEventBus, container: ModContainer) {
    init {
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
        if (MobileEnvironment.isMobileEnabled()) {
            modBus.addListener<FMLClientSetupEvent> { event ->
                event.enqueueWork {
                    CompatibilityDiagnostics.infoOnce(
                        "mobile-gl-runtime",
                        "TeutonLib: GL vendor='{}', renderer='{}', version='{}'",
                        GL11.glGetString(GL11.GL_VENDOR) ?: "unknown",
                        GL11.glGetString(GL11.GL_RENDERER) ?: "unknown",
                        GL11.glGetString(GL11.GL_VERSION) ?: "unknown"
                    )
                }
            }
        }
    }
}
