package de.TeutonStudio.TeutonLib.integration.peripheral.simulated

import dan200.computercraft.api.peripheral.PeripheralCapability
import dev.simulated_team.simulated.index.SimBlockEntityTypes
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent

object DirectionalGearshiftIntegration {
    fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        event.registerBlockEntity(
            PeripheralCapability.get(),
            SimBlockEntityTypes.DIRECTIONAL_GEARSHIFT.get()
        ) { block, _ -> DirectionalGearshiftPeripheral(block) }
    }
}
