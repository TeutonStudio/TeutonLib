package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dan200.computercraft.api.peripheral.IPeripheral
import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.gyroscopic_propeller_bearing.GyroscopicPropellerBearingBlockEntity

class GyroscopicBearingPeripheral(val blockEntity: GyroscopicPropellerBearingBlockEntity) : IPeripheral {
    override fun getType(): String = "gyroscopic_propeller_bearing"
    override fun getTarget(): Any = blockEntity
    override fun equals(other: IPeripheral?): Boolean =
        other is GyroscopicBearingPeripheral && other.blockEntity === blockEntity
}
