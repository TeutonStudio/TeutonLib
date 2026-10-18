package de.TeutonStudio.TeutonLib.integration.computer

import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.api.peripheral.WorkMonitor
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import dan200.computercraft.shared.computer.core.ServerComputer
import de.TeutonStudio.TeutonLib.TeutonLib
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import java.util.Collections
import java.util.IdentityHashMap

object ComputerIntegration {
    private val active = IdentityHashMap<WorkMonitor, ComputerRuntime>()
    private val restore = Collections.newSetFromMap(IdentityHashMap<BlockEntity, Boolean>())
    @JvmStatic fun register(bus: IEventBus) {
        bus.addListener { event: FMLCommonSetupEvent -> event.enqueueWork { ComputerCraftAPI.registerAPIFactory { ComputerRuntime(it) } } }
        bus.addListener { event: RegisterPayloadHandlersEvent -> ComputerPayloads.register(event) }
        bus.addListener { event: RegisterPayloadHandlersEvent -> WorkspacePayloads.registerPayloads(event) }
        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Post -> tick(event) }
        NeoForge.EVENT_BUS.addListener { _: ServerStoppedEvent -> synchronized(active) { active.clear() }; restore.clear() }
    }
    @JvmStatic fun start(runtime: ComputerRuntime) { synchronized(active) { active[runtime.system().mainThreadMonitor] = runtime } }
    @JvmStatic fun stop(runtime: ComputerRuntime) { synchronized(active) { active.remove(runtime.system().mainThreadMonitor) } }
    @JvmStatic fun runtime(computer: ServerComputer): ComputerRuntime? = synchronized(active) { active[computer.mainThreadMonitor] }
    @JvmStatic fun active(): List<ComputerRuntime> = synchronized(active) { java.util.List.copyOf(active.values) }
    @JvmStatic fun loaded(host: BlockEntity?) {
        if (host is ChannelHost && (host is ComputerBlockEntity || host.`teutonlib$channels`().entries().isNotEmpty())
            && host.level?.isClientSide == false) restore.add(host)
    }
    @JvmStatic fun removed(host: BlockEntity?) { restore.remove(host) }
    private fun tick(event: ServerTickEvent.Post) {
        for (host in restore.toList()) {
            restore.remove(host)
            if (host.isRemoved || host.level == null) continue
            try {
                (host as ChannelHost).`teutonlib$channels`().entries().filter { it.direction == "output" }
                    .forEach { DbwAccess.set(host, it.name, it.value) }
            } catch (e: RuntimeException) { TeutonLib.LOGGER.error("Could not restore computer DbW outputs", e) }
        }
    }
}
