package de.TeutonStudio.TeutonLib.integration.energy;

import java.util.function.Supplier;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** A receiving-only view. Resolve on every call so cached views cannot retain an old motor. */
public final class MotorEnergyInput implements IEnergyStorage {
    private final Supplier<IEnergyStorage> target;

    public MotorEnergyInput(Supplier<IEnergyStorage> target) {
        this.target = target;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        var storage = target.get();
        return storage == null ? 0 : storage.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) { return 0; }

    @Override
    public int getEnergyStored() {
        var storage = target.get();
        return storage == null ? 0 : storage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        var storage = target.get();
        return storage == null ? 0 : storage.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() { return false; }

    @Override
    public boolean canReceive() {
        var storage = target.get();
        return storage != null && storage.canReceive();
    }
}
