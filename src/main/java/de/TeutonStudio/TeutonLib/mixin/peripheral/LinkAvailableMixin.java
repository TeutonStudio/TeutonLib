package de.TeutonStudio.TeutonLib.mixin.peripheral;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkLuaApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Makes transfer targets visible to local peripherals, wired wrappers, and API factories alike. */
@Mixin(targets = {"dan200.computercraft.shared.computer.core.ComputerSystem",
        "dan200.computercraft.core.apis.PeripheralAPI$PeripheralWrapper",
        "dan200.computercraft.shared.peripheral.modem.wired/WiredModemPeripheral$RemotePeripheralWrapper"}, remap = false)
public abstract class LinkAvailableMixin {
    @Inject(method = "getAvailablePeripherals", at = @At("RETURN"), cancellable = true)
    private void teutonlib$all(CallbackInfoReturnable<Map<String, IPeripheral>> cir) {
        Map<String, IPeripheral> links = LinkLuaApi.available(((IComputerAccess) this).getMainThreadMonitor());
        if (links.isEmpty()) return;
        var result = new LinkedHashMap<>(cir.getReturnValue()); result.putAll(links); cir.setReturnValue(result);
    }
    @Inject(method = "getAvailablePeripheral", at = @At("RETURN"), cancellable = true)
    private void teutonlib$one(String name, CallbackInfoReturnable<IPeripheral> cir) {
        IPeripheral link = LinkLuaApi.available(((IComputerAccess) this).getMainThreadMonitor()).get(name);
        if (link != null) cir.setReturnValue(link);
    }
}
