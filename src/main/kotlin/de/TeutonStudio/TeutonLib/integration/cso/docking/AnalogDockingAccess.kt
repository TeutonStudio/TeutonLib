package de.TeutonStudio.TeutonLib.integration.cso.docking

import net.minecraft.core.BlockPos
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity.DockingConnectorState

/** Implemented on the existing connector by the version-gated Mixin. */
interface AnalogDockingAccess {
    var analogActive: Boolean
    var requestedLength: DockingLength
    var effectiveLength: DockingLength
    var selectedPartner: BlockPos?
    var dockingState: DockingConnectorState
    var closestDistance: Double
    fun updateDockingState()
}
