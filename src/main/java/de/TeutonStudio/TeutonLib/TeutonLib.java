package de.TeutonStudio.TeutonLib;

import com.mojang.logging.LogUtils;
import de.TeutonStudio.TeutonLib.integration.CsoAvailability;
import de.TeutonStudio.TeutonLib.integration.CsoRuntime;
import de.TeutonStudio.TeutonLib.integration.energy.NewAgeMotorEnergy;
import de.TeutonStudio.TeutonLib.network.CsoStatePayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(TeutonLib.MOD_ID)
public final class TeutonLib {
    public static final String MOD_ID = "teutonlib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TeutonLib(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, ServerConfig.FILE_NAME);
        modBus.addListener(this::configLoaded);
        modBus.addListener(this::configReloaded);
        modBus.addListener(this::payloads);
        modBus.addListener(NewAgeMotorEnergy::registerCapabilities);
        if (net.neoforged.fml.ModList.get().isLoaded("computercraft") && net.neoforged.fml.ModList.get().isLoaded("create"))
            de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinks.register(modBus);
        if (net.neoforged.fml.ModList.get().isLoaded("computercraft"))
            de.TeutonStudio.TeutonLib.integration.computer.ComputerIntegration.register(modBus);
        NeoForge.EVENT_BUS.register(CsoRuntime.class);
        var problems = CsoAvailability.problems();
        if (problems.isEmpty()) LOGGER.info("TeutonLib: CSO integration available");
        else LOGGER.info("TeutonLib: CSO integration unavailable: {}", String.join("; ", problems));
    }

    private void configLoaded(ModConfigEvent.Loading event) { configChanged(event); }
    private void configReloaded(ModConfigEvent.Reloading event) { configChanged(event); }
    private void configChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) CsoRuntime.configChanged();
    }
    private void payloads(RegisterPayloadHandlersEvent event) {
        CsoStatePayload.register(event);
    }
}
