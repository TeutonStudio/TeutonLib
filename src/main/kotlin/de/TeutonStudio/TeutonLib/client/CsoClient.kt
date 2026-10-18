package de.TeutonStudio.TeutonLib.client

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.CsoAvailability
import de.TeutonStudio.TeutonLib.integration.CsoState
import de.TeutonStudio.TeutonLib.integration.cso.bridge.ClientGearBridgeManager
import de.TeutonStudio.TeutonLib.integration.cso.bridge.ClientGearRegistry
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut
import net.minecraft.world.level.Level
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.level.LevelEvent

@EventBusSubscriber(modid = TeutonLib.Companion.MOD_ID, value = [Dist.CLIENT])
object CsoClient {
    private val STATE: CsoState = CsoState()
    @JvmStatic
    fun enabled(): Boolean {
        return CsoClient.STATE.enabled()
    }

    @JvmStatic
    fun apply(enabled: Boolean) {
        CsoClient.STATE.apply(
            enabled,
            CsoAvailability.available(),
            java.lang.Runnable { ClientGearBridgeManager.clear() })
    }

    @SubscribeEvent
    fun logout(event: LoggingOut?) {
        CsoClient.apply(false)
        if (CsoAvailability.available()) {
            ClientGearBridgeManager.clear()
            ClientGearRegistry.clear()
        }
    }

    @SubscribeEvent
    fun unload(event: net.neoforged.neoforge.event.level.LevelEvent.Unload) {
        val level = event.level as? Level ?: return
        if (level.isClientSide && CsoAvailability.available()) {
            ClientGearBridgeManager.removeLevel(level)
            ClientGearRegistry.removeLevel(level)
        }
    }
}
