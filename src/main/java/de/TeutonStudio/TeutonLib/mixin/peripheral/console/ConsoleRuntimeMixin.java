package de.TeutonStudio.TeutonLib.mixin.peripheral.console;

import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.LuaException;
import de.TeutonStudio.TeutonLib.integration.peripheral.*;
import de.teutonstudio.ccaeroworks.computer.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(value = PeripheralNetworkRuntime.class, remap = false)
public abstract class ConsoleRuntimeMixin implements ConsoleRuntimeAccess {
    @Shadow @Final private LinkedHashMap<String, PeripheralBinding> bindings;
    @Shadow @Final private IComputerSystem system;
    @Shadow @Final private ComputerConsoleAccess access;
    @Unique private volatile boolean teutonlib$active;
    @Shadow private void queueDetached(PeripheralNetworkNode node) { throw new AssertionError(); }
    @Shadow private void invalidateGraph(boolean queueEvents) { throw new AssertionError(); }
    @Override public synchronized Map<String, LinkDevice> teutonlib$devices() { return ConsoleLinkDevices.devices(Map.copyOf(bindings)); }
    @Override public synchronized void teutonlib$activate() {
        teutonlib$active = true;
        LinkRegistry.console(system, this);
        var owner = access.owner();
        if (owner != null) PeripheralNetworkRuntimes.INSTANCE.register(owner, (PeripheralNetworkRuntime) (Object) this);
    }
    @Override public synchronized void teutonlib$refresh() {
        if (!teutonlib$active) return;
        // A call can invalidate a handle between two graph refreshes. Retire that
        // attachment before the next graph is built, even if its device has returned.
        var iterator = bindings.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!entry.getKey().startsWith("cc_aeroworks:link_") || entry.getValue().checkValid()) continue;
            entry.getValue().close(); queueDetached(entry.getValue().getNode()); iterator.remove();
        }
        try { ((PeripheralNetworkRuntime) (Object) this).graph(true); }
        catch (Exception unavailable) {
            if (unavailable instanceof LuaException) invalidateGraph(true);
            else throw new RuntimeException(unavailable);
        }
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void teutonlib$tick(CallbackInfo ci) { if (!teutonlib$active) ci.cancel(); }
    @Inject(method = "close", at = @At("HEAD"))
    private void teutonlib$closed(CallbackInfo ci) { teutonlib$active = false; LinkRegistry.removeConsole(system); }
    @Inject(method = "describePeripheral", at = @At("RETURN"), cancellable = true)
    private void teutonlib$describe(PeripheralNetworkNode node, CallbackInfoReturnable<Map<String, Object>> cir) {
        var result = new LinkedHashMap<>(cir.getReturnValue());
        LinkRegistry.Connection connection = LinkRegistry.connection(node.getAddress());
        if (connection != null) result.putAll(LinkLuaApi.describe(connection));
        else result.put("connectionType", "adjacent");
        cir.setReturnValue(result);
    }
}
