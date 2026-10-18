package example.teutonlib

import dan200.computercraft.api.lua.IComputerSystem
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.ChannelProvider
import de.TeutonStudio.TeutonLib.api.computer.ComputerExtensions
import de.TeutonStudio.TeutonLib.api.computer.SourceProvider
import java.util.function.Function
import net.minecraft.world.level.block.entity.BlockEntity

/** Call once from common setup, after checking that TeutonLib and CC are loaded.  */
object TemplateComputerIntegration {
    @JvmStatic
    fun register(
        ownedHost: Function<IComputerSystem, BlockEntity?>,
        sources: SourceProvider, channels: ChannelProvider
    ) {
        ComputerExtensions.registerHosts(ownedHost)
        ComputerExtensions.registerSources(sources)
        ComputerExtensions.registerChannels(channels)
    }
}
