package example.teutonlib;

import dan200.computercraft.api.lua.IComputerSystem;
import de.TeutonStudio.TeutonLib.api.computer.ChannelProvider;
import de.TeutonStudio.TeutonLib.api.computer.ComputerExtensions;
import de.TeutonStudio.TeutonLib.api.computer.SourceProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.function.Function;

/** Call once from common setup, after checking that TeutonLib and CC are loaded. */
public final class TemplateComputerIntegration {
    public static void register(Function<IComputerSystem, BlockEntity> ownedHost,
                                SourceProvider sources, ChannelProvider channels) {
        ComputerExtensions.registerHosts(ownedHost);
        ComputerExtensions.registerSources(sources);
        ComputerExtensions.registerChannels(channels);
    }
    private TemplateComputerIntegration() {}
}
