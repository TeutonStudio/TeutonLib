package de.TeutonStudio.TeutonLib.client;

import de.TeutonStudio.TeutonLib.TeutonLib;
import de.TeutonStudio.TeutonLib.integration.CsoAvailability;
import de.TeutonStudio.TeutonLib.integration.CsoState;
import de.TeutonStudio.TeutonLib.integration.cso.bridge.ClientGearBridgeManager;
import de.TeutonStudio.TeutonLib.integration.cso.bridge.ClientGearRegistry;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = TeutonLib.MOD_ID, value = Dist.CLIENT)
public final class CsoClient {
    private static final CsoState STATE = new CsoState();
    private CsoClient() {}

    public static boolean enabled() { return STATE.enabled(); }
    public static void apply(boolean enabled) {
        STATE.apply(enabled, CsoAvailability.available(), () -> ClientGearBridgeManager.INSTANCE.clear());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        apply(false);
        if (CsoAvailability.available()) {
            ClientGearBridgeManager.INSTANCE.clear();
            ClientGearRegistry.INSTANCE.clear();
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide && CsoAvailability.available()) {
            ClientGearBridgeManager.INSTANCE.removeLevel(level);
            ClientGearRegistry.INSTANCE.removeLevel(level);
        }
    }
}
