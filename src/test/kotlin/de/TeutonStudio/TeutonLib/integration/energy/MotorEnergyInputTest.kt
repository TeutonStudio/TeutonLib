package de.TeutonStudio.TeutonLib.integration.energy

import java.util.concurrent.atomic.AtomicReference
import net.neoforged.neoforge.energy.IEnergyStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MotorEnergyInputTest {
    @Test
    fun forwardsSimulationAndRespectsTargetCapacityAndRate() {
        val motor = Battery()
        val input = MotorEnergyInput(java.util.function.Supplier { motor })
        assertEquals(60, input.receiveEnergy(500, true))
        assertEquals(0, motor.stored)
        assertEquals(60, input.receiveEnergy(500, false))
        assertEquals(40, input.receiveEnergy(500, false))
        assertEquals(0, input.receiveEnergy(500, false))
        assertEquals(100, input.energyStored)
        assertEquals(100, input.maxEnergyStored)
    }

    @Test
    fun cachedViewObservesDisconnectDisableReconnectAndReplacement() {
        val first = Battery()
        val target: AtomicReference<IEnergyStorage> = AtomicReference<IEnergyStorage>(first)
        val input = MotorEnergyInput(java.util.function.Supplier { target.get() })
        assertTrue(input.canReceive())
        assertEquals(25, input.receiveEnergy(25, false))
        target.set(null)
        assertFalse(input.canReceive())
        assertEquals(0, input.receiveEnergy(25, false))
        assertEquals(0, input.energyStored)
        assertEquals(0, input.maxEnergyStored)
        assertEquals(25, first.stored)
        val second = Battery()
        target.set(second)
        assertTrue(input.canReceive())
        assertEquals(15, input.receiveEnergy(15, false))
        assertEquals(15, second.stored)
        assertEquals(25, first.stored)
    }

    @Test
    fun cannotExtractOrInsertNegativeAmounts() {
        val motor = Battery()
        motor.stored = 50
        val input = MotorEnergyInput(java.util.function.Supplier { motor })
        assertFalse(input.canExtract())
        assertEquals(0, input.extractEnergy(50, false))
        assertEquals(0, input.extractEnergy(50, true))
        assertEquals(0, input.receiveEnergy(-100, false))
        assertEquals(0, input.receiveEnergy(0, false))
        assertEquals(50, motor.stored)
    }

    private class Battery : IEnergyStorage {
        var stored: kotlin.Int = 0
        override fun receiveEnergy(amount: kotlin.Int, simulate: Boolean): kotlin.Int {
            val accepted = kotlin.math.min(amount, kotlin.math.min(60, 100 - this.stored))
            if (!simulate) this.stored += accepted
            return accepted
        }

        override fun extractEnergy(amount: kotlin.Int, simulate: Boolean): kotlin.Int {
            val extracted = kotlin.math.min(amount, this.stored)
            if (!simulate) this.stored -= extracted
            return extracted
        }

        override fun getEnergyStored(): Int = stored
        override fun getMaxEnergyStored(): Int = 100

        override fun canExtract(): Boolean {
            return true
        }

        override fun canReceive(): Boolean {
            return true
        }
    }
}
