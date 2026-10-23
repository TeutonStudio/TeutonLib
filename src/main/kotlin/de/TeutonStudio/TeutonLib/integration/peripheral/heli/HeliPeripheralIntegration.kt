package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.api.peripheral.PeripheralCapability
import dev.eriksonn.aeronautics.index.AeroBlockEntityTypes
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent

object HeliPeripheralIntegration {
    fun registerGenericMethods() {
        ComputerCraftAPI.registerGenericSource(BearingGenericMethods)
    }

    fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        event.registerBlockEntity(
            PeripheralCapability.get(),
            AeroBlockEntityTypes.GYROSCOPIC_PROPELLER_BEARING.get()
        ) { block, _ -> GyroscopicBearingPeripheral(block) }
    }
}
