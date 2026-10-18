package de.TeutonStudio.TeutonLib.api.computer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import de.TeutonStudio.TeutonLib.TeutonLib;
/** Register once during common setup. The returned handle allows an integration to unregister. */
public final class ComputerExtensions {
    private static final List<java.util.function.Function<dan200.computercraft.api.lua.IComputerSystem, net.minecraft.world.level.block.entity.BlockEntity>> HOSTS = new CopyOnWriteArrayList<>();
    /** Register an addon host resolver. Return null unless the system belongs to this host. */
    public static AutoCloseable registerHosts(java.util.function.Function<dan200.computercraft.api.lua.IComputerSystem, net.minecraft.world.level.block.entity.BlockEntity> resolver) {
        HOSTS.add(resolver); return () -> HOSTS.remove(resolver);
    }
    public static net.minecraft.world.level.block.entity.BlockEntity resolveHost(dan200.computercraft.api.lua.IComputerSystem system) {
        for (var provider : HOSTS) { var host = provider.apply(system); if (host != null) return host; }
        return null;
    }
    private static final List<SourceProvider> SOURCES = new CopyOnWriteArrayList<>();
    private static final List<ChannelProvider> CHANNELS = new CopyOnWriteArrayList<>();
    public static AutoCloseable registerSources(SourceProvider provider) { SOURCES.add(provider); return () -> SOURCES.remove(provider); }
    public static AutoCloseable registerChannels(ChannelProvider provider) { CHANNELS.add(provider); return () -> CHANNELS.remove(provider); }
    public static void sources(ComputerContext computer, Consumer<SourceView> sink) {
        SOURCES.forEach(provider -> { try { provider.sources(computer).forEach(sink); }
            catch (RuntimeException e) { TeutonLib.LOGGER.warn("Computer source provider failed", e); } });
    }
    public static void channels(ComputerContext computer, Consumer<ChannelView> sink) {
        CHANNELS.forEach(provider -> { try { provider.channels(computer).forEach(sink); }
            catch (RuntimeException e) { TeutonLib.LOGGER.warn("Computer channel provider failed", e); } });
    }
    private ComputerExtensions() {}
}
