package de.TeutonStudio.TeutonLib

import com.mojang.logging.LogUtils
import de.TeutonStudio.TeutonLib.integration.CsoAvailability
import de.TeutonStudio.TeutonLib.integration.CsoRuntime
import de.TeutonStudio.TeutonLib.integration.computer.ComputerIntegration
import de.TeutonStudio.TeutonLib.integration.energy.NewAgeMotorEnergy
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinks
import de.TeutonStudio.TeutonLib.integration.peripheral.heli.HeliPeripheralIntegration
import de.TeutonStudio.TeutonLib.network.CsoStatePayload
import java.util.function.Consumer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.fml.event.config.ModConfigEvent.Loading
import net.neoforged.fml.event.config.ModConfigEvent.Reloading
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.Logger

@Mod(TeutonLib.Companion.MOD_ID)
class TeutonLib(modBus: IEventBus, container: ModContainer) {
    init {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, ServerConfig.FILE_NAME)
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, ClientConfig.FILE_NAME)
        modBus.addListener<Loading>(Consumer { event: Loading? -> this.configLoaded(event!!) })
        modBus.addListener<Reloading>(Consumer { event: Reloading? -> this.configReloaded(event!!) })
        modBus.addListener<RegisterPayloadHandlersEvent>(Consumer { event: RegisterPayloadHandlersEvent? ->
            this.payloads(
                event!!
            )
        })
        modBus.addListener<RegisterCapabilitiesEvent>(Consumer { event: RegisterCapabilitiesEvent? ->
            NewAgeMotorEnergy.registerCapabilities(event!!)
            if (ModList.get().isLoaded("computercraft") && ModList.get().isLoaded("aeronautics") &&
                ModList.get().isLoaded("simulated") && ModList.get().isLoaded("sable")
            ) HeliPeripheralIntegration.registerCapabilities(event)
        })
        if (ModList.get().isLoaded("computercraft") && ModList.get()
                .isLoaded("create")
        ) PeripheralLinks.register(modBus)
        if (ModList.get().isLoaded("computercraft")) ComputerIntegration.register(modBus)
        NeoForge.EVENT_BUS.register(CsoRuntime::class.java)
        val problems = CsoAvailability.problems()
        if (problems.isEmpty()) LOGGER.info("TeutonLib: CSO integration available")
        else LOGGER.info("TeutonLib: CSO integration unavailable: {}", problems.joinToString("; "))
    }

    private fun configLoaded(event: Loading) {
        configChanged(event)
    }

    private fun configReloaded(event: Reloading) {
        configChanged(event)
    }

    private fun configChanged(event: ModConfigEvent) {
        if (event.config.spec === ServerConfig.SPEC) CsoRuntime.configChanged()
    }

    private fun payloads(event: RegisterPayloadHandlersEvent) {
        CsoStatePayload.Companion.register(event)
    }

    companion object {
        const val MOD_ID: kotlin.String = "teutonlib"
        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
