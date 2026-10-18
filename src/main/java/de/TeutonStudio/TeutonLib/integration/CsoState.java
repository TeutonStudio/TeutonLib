package de.TeutonStudio.TeutonLib.integration;

/** Owned by one logical side's main thread. Cleanup sees the old active state. */
public final class CsoState {
    private volatile boolean enabled;

    public boolean enabled() { return enabled; }

    public boolean apply(boolean requested, boolean available, Runnable removeBridges) {
        boolean next = requested && available;
        if (enabled == next) return false;
        if (!next) removeBridges.run();
        enabled = next;
        return true;
    }
}
