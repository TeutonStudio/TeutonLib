package de.TeutonStudio.TeutonLib.client

import de.TeutonStudio.TeutonLib.TeutonLib
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

/** NeoForge's built-in editor supports our ModConfigSpec without another GUI mod. */
@Mod(value = TeutonLib.MOD_ID, dist = [Dist.CLIENT])
class TeutonLibClient(container: ModContainer) {
    init {
        container.registerExtensionPoint(
            IConfigScreenFactory::class.java,
            IConfigScreenFactory { mod, parent -> ConfigurationScreen(mod, parent) }
        )
    }
}
