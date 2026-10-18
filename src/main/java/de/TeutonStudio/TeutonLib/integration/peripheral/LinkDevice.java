package de.TeutonStudio.TeutonLib.integration.peripheral;

import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.Map;
import java.util.Set;

public interface LinkDevice {
    IPeripheral peripheral();
    Set<String> methods();
    MethodResult call(ILuaContext context, String method, IArguments arguments) throws LuaException;
    Object handle();
    Map<String, Object> info();
    default void close() {}
}
