package de.TeutonStudio.TeutonLib.api.computer;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import java.util.Map;

/** Server-thread view of an active computer. Peripheral objects are borrowed, never attached here. */
public interface ComputerContext {
    int computerId();
    ServerLevel level();
    BlockPos position();
    Map<String, IPeripheral> peripherals();
    void queueEvent(String event, Object... arguments);
}
