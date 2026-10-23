package de.TeutonStudio.TeutonLib.api.computer

import dan200.computercraft.api.lua.IComputerSystem
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.computer.WorkspacePayloads
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.api.peripheral.IPeripheral
import de.TeutonStudio.TeutonLib.integration.computer.ComputerIntegration
import de.TeutonStudio.TeutonLib.integration.computer.StandardComputerWorkspace
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/** Register during common setup. Close the returned handle to unregister. */
object ComputerExtensions {
    private val peripheralSources = CopyOnWriteArrayList<PeripheralSourceProvider>()
    private val documentation = CopyOnWriteArrayList<SourceDocumentationProvider>()
    private val hosts = CopyOnWriteArrayList<Function<IComputerSystem, BlockEntity?>>()
    private val sources = CopyOnWriteArrayList<SourceProvider>()
    private val channels = CopyOnWriteArrayList<ChannelProvider>()
    private val authorities = CopyOnWriteArrayList<Predicate<ComputerContext>>()

    @JvmStatic fun registerPeripheralSources(provider: PeripheralSourceProvider): AutoCloseable {
        peripheralSources.add(provider)
        return AutoCloseable { peripheralSources.remove(provider) }
    }
    @JvmStatic fun peripheralSources(computer: ComputerContext): Map<String, PeripheralSource> = buildMap {
        peripheralSources.forEach { provider ->
            try { provider.peripherals(computer).forEach { (name, value) -> putIfAbsent(name, value) } }
            catch (e: RuntimeException) { TeutonLib.LOGGER.warn("Peripheral source provider failed", e) }
        }
    }

    @JvmStatic fun registerSourceDocumentation(provider: SourceDocumentationProvider): AutoCloseable {
        documentation.add(provider)
        return AutoCloseable { documentation.remove(provider) }
    }
    @JvmStatic fun sourceDocumentation(computer: ComputerContext, source: SourceView): List<SourceMethod> =
        documentation.flatMap { provider ->
            try { provider.methods(computer, source) }
            catch (e: RuntimeException) { TeutonLib.LOGGER.warn("Source documentation provider failed", e); emptyList() }
        }.distinctBy { it.name }

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

    /** Stable projection helpers for addon workspaces; implementation runtime types never escape. */
    @JvmStatic fun sourceViews(computer: ServerComputer): List<SourceView> =
        ComputerIntegration.runtime(computer)?.sourceViews().orEmpty()

    @JvmStatic fun sourceEntry(
        computer: ServerComputer,
        source: SourceView,
        peripheral: IPeripheral? = null
    ): ComputerWorkspace.Entry {
        val runtime = checkNotNull(ComputerIntegration.runtime(computer)) { "Computer unavailable" }
        return StandardComputerWorkspace.sourceEntry(runtime, source, peripheral ?: runtime.peripherals()[source.name])
    }
}
