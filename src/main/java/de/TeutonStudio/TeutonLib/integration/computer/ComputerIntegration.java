package de.TeutonStudio.TeutonLib.integration.computer;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.peripheral.WorkMonitor;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import de.TeutonStudio.TeutonLib.TeutonLib;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

public final class ComputerIntegration {
    private static final Map<WorkMonitor, ComputerRuntime> ACTIVE = new IdentityHashMap<>();
    private static final Set<BlockEntity> RESTORE = Collections.newSetFromMap(new IdentityHashMap<>());
    public static void register(IEventBus bus) {
        bus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> ComputerCraftAPI.registerAPIFactory(ComputerRuntime::new)));
        bus.addListener(ComputerPayloads::register);
        NeoForge.EVENT_BUS.addListener(ComputerIntegration::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> { ACTIVE.clear(); RESTORE.clear(); });
    }
    static void start(ComputerRuntime runtime) { synchronized (ACTIVE) { ACTIVE.put(runtime.system().getMainThreadMonitor(), runtime); } }
    static void stop(ComputerRuntime runtime) { synchronized (ACTIVE) { ACTIVE.remove(runtime.system().getMainThreadMonitor(), runtime); } }
    public static ComputerRuntime runtime(ServerComputer computer) {
        synchronized (ACTIVE) { return ACTIVE.get(computer.getMainThreadMonitor()); }
    }
    static List<ComputerRuntime> active() { synchronized (ACTIVE) { return List.copyOf(ACTIVE.values()); } }
    public static void loaded(BlockEntity host) {
        if (host instanceof ChannelHost channels && (host instanceof ComputerBlockEntity || !channels.teutonlib$channels().entries().isEmpty())
            && host.getLevel() != null && !host.getLevel().isClientSide) RESTORE.add(host);
    }
    public static void removed(BlockEntity host) { RESTORE.remove(host); }
    private static void tick(ServerTickEvent.Post event) {
        for (BlockEntity host : List.copyOf(RESTORE)) {
            RESTORE.remove(host);
            if (host.isRemoved() || host.getLevel() == null) continue;
            try {
                for (var entry : ((ChannelHost)host).teutonlib$channels().entries())
                    if (entry.direction().equals("output")) DbwAccess.set(host, entry.name(), entry.value());
            } catch (RuntimeException e) { TeutonLib.LOGGER.error("Could not restore computer DbW outputs", e); }
        }
    }
    private ComputerIntegration() {}
}
