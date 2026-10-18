package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.cso.bridge.GearBridgeManager
import de.TeutonStudio.TeutonLib.integration.cso.bridge.GearRegistry
import de.TeutonStudio.TeutonLib.network.CsoStatePayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.network.PacketDistributor

/** No optional class is accessed until availability has been checked.  */
object CsoRuntime {
    private val STATE = CsoState()

    @Volatile
    private var server: MinecraftServer? = null

    @JvmStatic
    fun enabled(): Boolean {
        return CsoRuntime.STATE.enabled()
    }

    @SubscribeEvent
    @JvmStatic
    fun starting(event: ServerAboutToStartEvent) {
        CsoRuntime.server = event.server
        CsoRuntime.applyConfig(false)
    }

    @JvmStatic
    fun configChanged() {
        val current: MinecraftServer? = CsoRuntime.server
        if (current == null) return  // Remote client's synced config must not change server state.

        current.execute(java.lang.Runnable {
            if (CsoRuntime.server === current && ServerConfig.SPEC.isLoaded()) CsoRuntime.applyConfig(true)
        })
    }

    @JvmStatic
    private fun applyConfig(broadcast: Boolean) {
        val changed = CsoRuntime.STATE.apply(
            ServerConfig.CSO_ENABLED.asBoolean, CsoAvailability.available(),
            java.lang.Runnable { GearBridgeManager.clear() })
        if (changed) {
            TeutonLib.Companion.LOGGER.info("TeutonLib CSO: {}", if (CsoRuntime.enabled()) "enabled" else "disabled")
            if (broadcast) PacketDistributor.sendToAllPlayers(CsoStatePayload(CsoRuntime.enabled()))
        }
    }

    /** Called by commands on the server thread; file watcher reloads use the same transition.  */
    @JvmStatic
    fun setRequested(enabled: Boolean) {
        ServerConfig.CSO_ENABLED.set(enabled)
        ServerConfig.SPEC.save()
        CsoRuntime.applyConfig(true)
    }

    @SubscribeEvent
    @JvmStatic
    fun commands(event: RegisterCommandsEvent) {
        CsoCommands.register(event.dispatcher)
    }

    @SubscribeEvent
    @JvmStatic
    fun login(event: PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        PacketDistributor.sendToPlayer(
            player,
            CsoStatePayload(CsoRuntime.enabled())
        )
    }

    @SubscribeEvent
    @JvmStatic
    fun unload(event: net.neoforged.neoforge.event.level.LevelEvent.Unload) {
        val level = event.level as? Level ?: return
        if (!level.isClientSide && CsoAvailability.available()) {
            GearBridgeManager.removeLevel(level)
            GearRegistry.removeLevel(level)
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun stopping(event: ServerStoppingEvent?) {
        CsoRuntime.STATE.apply(
            false,
            CsoAvailability.available(),
            java.lang.Runnable { GearBridgeManager.clear() })
        CsoRuntime.server = null // Ignore any file watcher work already queued for the old world.
    }

    @SubscribeEvent
    @JvmStatic
    fun stopped(event: ServerStoppedEvent?) {
        if (CsoAvailability.available()) {
            GearBridgeManager.clear()
            GearRegistry.clear()
        }
        CsoRuntime.server = null
    }
}
