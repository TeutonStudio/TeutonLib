package de.TeutonStudio.TeutonLib.integration.energy

import java.util.function.Supplier
import net.neoforged.neoforge.energy.IEnergyStorage

/** A receiving-only view. Resolve on every call so cached views cannot retain an old motor.  */
class MotorEnergyInput(private val target: Supplier<IEnergyStorage?>) : IEnergyStorage {
    override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int {
        if (maxReceive <= 0) return 0
        val storage = target.get()
        return if (storage == null) 0 else storage.receiveEnergy(maxReceive, simulate)
    }

    override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int {
        return 0
    }

    override fun getEnergyStored(): Int {
        val storage = target.get()
        return if (storage == null) 0 else storage.energyStored
    }

    override fun getMaxEnergyStored(): Int {
        val storage = target.get()
        return if (storage == null) 0 else storage.maxEnergyStored
    }

    override fun canExtract(): Boolean {
        return false
    }

    override fun canReceive(): Boolean {
        val storage = target.get()
        return storage != null && storage.canReceive()
    }
}
