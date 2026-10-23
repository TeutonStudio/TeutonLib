package de.TeutonStudio.TeutonLib.api.simulated

import de.TeutonStudio.TeutonLib.integration.cso.bridge.SimulatedDockingAccess
import net.minecraft.world.level.block.entity.BlockEntity

/** Dependency-safe Minecraft-only facade for Simulated docking connector state. */
object SimulatedDockingConnector {
    @JvmStatic fun available(): Boolean = SimulatedDockingAccess.available()
    @JvmStatic fun isConnector(blockEntity: BlockEntity): Boolean = SimulatedDockingAccess.isConnector(blockEntity)
    @JvmStatic fun isLocked(blockEntity: BlockEntity): Boolean = SimulatedDockingAccess.locked(blockEntity)
    @JvmStatic fun isExtended(blockEntity: BlockEntity): Boolean = SimulatedDockingAccess.extended(blockEntity)
    @JvmStatic fun isRetracted(blockEntity: BlockEntity): Boolean = SimulatedDockingAccess.retracted(blockEntity)
    @JvmStatic fun hasOtherConnector(blockEntity: BlockEntity): Boolean = SimulatedDockingAccess.hasOther(blockEntity)
    @JvmStatic fun otherConnector(blockEntity: BlockEntity): BlockEntity? = SimulatedDockingAccess.other(blockEntity)
}
