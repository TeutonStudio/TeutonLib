package de.TeutonStudio.TeutonLib.integration.peripheral;

import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.IPeripheral;
import de.teutonstudio.ccaeroworks.computer.PeripheralBinding;
import de.teutonstudio.ccaeroworks.computer.PeripheralLuaHandle;
import java.util.*;

/** Views onto the console runtime's existing attachments: never calls attach a second time. */
public final class ConsoleLinkDevices {
    public static Map<String, LinkDevice> devices(Map<String, PeripheralBinding> bindings) {
        var result = new LinkedHashMap<String, LinkDevice>();
        bindings.forEach((name, binding) -> {
            if (!name.startsWith("cc_aeroworks:link_")) return;
            result.put(name, new LinkDevice() {
                @Override public IPeripheral peripheral() { return binding.getNode().getTarget(); }
                @Override public Set<String> methods() { return binding.getMethodNames(); }
                @Override public MethodResult call(ILuaContext context, String method, IArguments arguments) throws LuaException {
                    return binding.call(context, method, arguments);
                }
                @Override public Object handle() { return new PeripheralLuaHandle(binding); }
                @Override public Map<String, Object> info() { return binding.info(); }
            });
        });
        return result;
    }
    private ConsoleLinkDevices() {}
}
