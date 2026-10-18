package de.TeutonStudio.TeutonLib.mixin.peripheral;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement;
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemPeripheral;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** CC's API-factory access normally exposes no peripherals at all. Supply the
 * already-connected sides and their wired networks without attaching anything. */
@Mixin(targets = "dan200.computercraft.shared.computer.core.ComputerSystem", remap = false)
public abstract class LinkSystemAccessMixin {
    @Shadow @Final private IAPIEnvironment environment;
    @Unique private Map<String, IPeripheral> teutonlib$reachable() {
        var result = new LinkedHashMap<String, IPeripheral>();
        for (ComputerSide side : ComputerSide.values()) {
            IPeripheral peripheral = environment.getPeripheral(side);
            if (peripheral == null) continue;
            result.put(side.getName(), peripheral);
            if (peripheral instanceof WiredModemPeripheral modem && modem.getNode().getElement() instanceof WiredModemElement element)
                result.putAll(element.getRemotePeripherals());
        }
        return result;
    }
    @Inject(method = "getAvailablePeripherals", at = @At("RETURN"), cancellable = true)
    private void teutonlib$all(CallbackInfoReturnable<Map<String, IPeripheral>> cir) {
        var result = new LinkedHashMap<>(cir.getReturnValue()); result.putAll(teutonlib$reachable()); cir.setReturnValue(result);
    }
    @Inject(method = "getAvailablePeripheral", at = @At("RETURN"), cancellable = true)
    private void teutonlib$one(String name, CallbackInfoReturnable<IPeripheral> cir) {
        if (cir.getReturnValue() == null) cir.setReturnValue(teutonlib$reachable().get(name));
    }
}
