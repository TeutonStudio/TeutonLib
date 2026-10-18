package de.TeutonStudio.TeutonLib.api.computer

import dan200.computercraft.api.lua.IComputerSystem
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.computer.WorkspacePayloads
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/** Register during common setup. Close the returned handle to unregister. */
object ComputerExtensions {
    private val hosts = CopyOnWriteArrayList<Function<IComputerSystem, BlockEntity?>>()
    private val sources = CopyOnWriteArrayList<SourceProvider>()
    private val channels = CopyOnWriteArrayList<ChannelProvider>()
    private val authorities = CopyOnWriteArrayList<Predicate<ComputerContext>>()

    @JvmStatic fun registerHosts(resolver: Function<IComputerSystem, BlockEntity?>): AutoCloseable {
        hosts.add(resolver)
        return AutoCloseable { hosts.remove(resolver) }
    }
    @JvmStatic fun resolveHost(system: IComputerSystem): BlockEntity? = hosts.firstNotNullOfOrNull { it.apply(system) }
    @JvmStatic fun registerSources(provider: SourceProvider): AutoCloseable {
        sources.add(provider)
        return AutoCloseable { sources.remove(provider) }
    }
    @JvmStatic fun registerChannels(provider: ChannelProvider): AutoCloseable {
        channels.add(provider)
        return AutoCloseable { channels.remove(provider) }
    }
    @JvmStatic fun sources(computer: ComputerContext, sink: Consumer<SourceView>) {
        sources.forEach { provider ->
            try { provider.sources(computer).forEach { sink.accept(it) } }
            catch (e: RuntimeException) { TeutonLib.LOGGER.warn("Computer source provider failed", e) }
        }
    }
    @JvmStatic fun channels(computer: ComputerContext, sink: Consumer<ChannelView>) {
        channels.forEach { provider ->
            try { provider.channels(computer).forEach { sink.accept(it) } }
            catch (e: RuntimeException) { TeutonLib.LOGGER.warn("Computer channel provider failed", e) }
        }
    }
    @JvmStatic fun hasWorkspace(id: ResourceLocation): Boolean = WorkspacePayloads.contains(id)
    @JvmStatic fun registerWorkspace(id: ResourceLocation, backend: ComputerWorkspace): AutoCloseable = WorkspacePayloads.register(id, backend)
    @JvmStatic fun registerChannelAuthority(owner: Predicate<ComputerContext>): AutoCloseable {
        authorities.add(owner)
        return AutoCloseable { authorities.remove(owner) }
    }
    @JvmStatic fun externalChannels(computer: ComputerContext): Boolean = authorities.any { it.test(computer) }
}
