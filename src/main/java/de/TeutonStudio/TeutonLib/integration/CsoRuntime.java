package de.TeutonStudio.TeutonLib.integration;

import de.TeutonStudio.TeutonLib.ServerConfig;
import de.TeutonStudio.TeutonLib.TeutonLib;
import de.TeutonStudio.TeutonLib.integration.cso.bridge.GearBridgeManager;
import de.TeutonStudio.TeutonLib.integration.cso.bridge.GearRegistry;
import de.TeutonStudio.TeutonLib.network.CsoStatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** No optional class is accessed until availability has been checked. */
public final class CsoRuntime {
    private static final CsoState STATE = new CsoState();
    private static volatile MinecraftServer server;

    private CsoRuntime() {}
    public static boolean enabled() { return STATE.enabled(); }

    @SubscribeEvent
    public static void starting(ServerAboutToStartEvent event) {
        server = event.getServer();
        applyConfig(false);
    }

    public static void configChanged() {
        MinecraftServer current = server;
        if (current == null) return; // Remote client's synced config must not change server state.
        current.execute(() -> {
            if (server == current && ServerConfig.SPEC.isLoaded()) applyConfig(true);
        });
    }

    private static void applyConfig(boolean broadcast) {
        boolean changed = STATE.apply(ServerConfig.CSO_ENABLED.getAsBoolean(), CsoAvailability.available(),
                () -> GearBridgeManager.INSTANCE.clear());
        if (changed) {
            TeutonLib.LOGGER.info("TeutonLib CSO: {}", enabled() ? "enabled" : "disabled");
            if (broadcast) PacketDistributor.sendToAllPlayers(new CsoStatePayload(enabled()));
        }
    }

    /** Called by commands on the server thread; file watcher reloads use the same transition. */
    public static void setRequested(boolean enabled) {
        ServerConfig.CSO_ENABLED.set(enabled);
        ServerConfig.SPEC.save();
        applyConfig(true);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) { CsoCommands.register(event.getDispatcher()); }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PacketDistributor.sendToPlayer(player, new CsoStatePayload(enabled()));
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide && CsoAvailability.available()) {
            GearBridgeManager.INSTANCE.removeLevel(level);
            GearRegistry.INSTANCE.removeLevel(level);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        STATE.apply(false, CsoAvailability.available(), () -> GearBridgeManager.INSTANCE.clear());
        server = null; // Ignore any file watcher work already queued for the old world.
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if (CsoAvailability.available()) {
            GearBridgeManager.INSTANCE.clear();
            GearRegistry.INSTANCE.clear();
        }
        server = null;
    }
}
