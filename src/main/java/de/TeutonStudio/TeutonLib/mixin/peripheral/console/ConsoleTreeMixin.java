package de.TeutonStudio.TeutonLib.mixin.peripheral.console;

import de.teutonstudio.ccaeroworks.computer.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Retains the tree's side keys for adjacent devices; wireless siblings use their stable names. */
@Mixin(targets = "de.teutonstudio.ccaeroworks.computer.PeripheralTreeKt", remap = false)
public abstract class ConsoleTreeMixin {
    @Inject(method = "describeTree", at = @At("RETURN"), cancellable = true)
    private static void teutonlib$tree(PeripheralNetworkRuntime runtime, CallbackInfoReturnable<Map<String, Object>> cir) {
        var graph = runtime.graph(false);
        var result = new LinkedHashMap<String, Object>();
        for (DeskNetworkNode desk : graph.getDesks()) {
            var children = new LinkedHashMap<String, Object>();
            for (PeripheralNetworkNode node : graph.getPeripherals()) {
                if (!node.getDesk().getAddress().equals(desk.getAddress())) continue;
                var info = new LinkedHashMap<>(runtime.describePeripheral(node));
                info.put("x", node.getPos().getX()); info.put("y", node.getPos().getY()); info.put("z", node.getPos().getZ());
                info.put("handle", runtime.peripheralHandle(node));
                children.put(node.getAddress().startsWith("cc_aeroworks:link_") ? node.getAddress() : node.getSide().name().toLowerCase(Locale.ROOT), info);
            }
            var info = new LinkedHashMap<>(runtime.describeDesk(desk.getAddress()));
            info.put("handle", runtime.deskHandle(desk)); info.put("peripherals", children);
            result.put(desk.getAddress(), info);
        }
        cir.setReturnValue(result);
    }
}
