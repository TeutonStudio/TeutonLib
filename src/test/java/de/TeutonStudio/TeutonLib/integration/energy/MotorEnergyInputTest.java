package de.TeutonStudio.TeutonLib.integration.energy;

import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotorEnergyInputTest {
    @Test
    void forwardsSimulationAndRespectsTargetCapacityAndRate() {
        var motor = new Battery();
        var input = new MotorEnergyInput(() -> motor);
        assertEquals(60, input.receiveEnergy(500, true));
        assertEquals(0, motor.energy);
        assertEquals(60, input.receiveEnergy(500, false));
        assertEquals(40, input.receiveEnergy(500, false));
        assertEquals(0, input.receiveEnergy(500, false));
        assertEquals(100, input.getEnergyStored());
        assertEquals(100, input.getMaxEnergyStored());
    }

    @Test
    void cachedViewObservesDisconnectDisableReconnectAndReplacement() {
        var first = new Battery();
        var target = new AtomicReference<IEnergyStorage>(first);
        var input = new MotorEnergyInput(target::get);
        assertTrue(input.canReceive());
        assertEquals(25, input.receiveEnergy(25, false));
        target.set(null);
        assertFalse(input.canReceive());
        assertEquals(0, input.receiveEnergy(25, false));
        assertEquals(0, input.getEnergyStored());
        assertEquals(0, input.getMaxEnergyStored());
        assertEquals(25, first.energy);
        var second = new Battery();
        target.set(second);
        assertTrue(input.canReceive());
        assertEquals(15, input.receiveEnergy(15, false));
        assertEquals(15, second.energy);
        assertEquals(25, first.energy);
    }

    @Test
    void cannotExtractOrInsertNegativeAmounts() {
        var motor = new Battery();
        motor.energy = 50;
        var input = new MotorEnergyInput(() -> motor);
        assertFalse(input.canExtract());
        assertEquals(0, input.extractEnergy(50, false));
        assertEquals(0, input.extractEnergy(50, true));
        assertEquals(0, input.receiveEnergy(-100, false));
        assertEquals(0, input.receiveEnergy(0, false));
        assertEquals(50, motor.energy);
    }

    private static final class Battery implements IEnergyStorage {
        int energy;
        public int receiveEnergy(int amount, boolean simulate) {
            int accepted = Math.min(amount, Math.min(60, 100 - energy));
            if (!simulate) energy += accepted;
            return accepted;
        }
        public int extractEnergy(int amount, boolean simulate) {
            int extracted = Math.min(amount, energy);
            if (!simulate) energy -= extracted;
            return extracted;
        }
        public int getEnergyStored() { return energy; }
        public int getMaxEnergyStored() { return 100; }
        public boolean canExtract() { return true; }
        public boolean canReceive() { return true; }
    }
}
