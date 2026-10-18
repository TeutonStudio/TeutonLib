package de.TeutonStudio.TeutonLib.mixin.peripheral

import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemPeripheral
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkLuaApi
import java.util.LinkedHashMap
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Makes transfer targets visible to local peripherals, wired wrappers, and API factories alike.  */
@Mixin(
    targets = ["dan200.computercraft.shared.computer.core.ComputerSystem", "dan200.computercraft.core.apis.PeripheralAPI\$PeripheralWrapper", "dan200.computercraft.shared.peripheral.modem.wired/WiredModemPeripheral\$RemotePeripheralWrapper"],
    remap = false
)
abstract class LinkAvailableMixin {
    @Inject(method = ["getAvailablePeripherals"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$all`(cir: CallbackInfoReturnable<MutableMap<String, IPeripheral>>) {
        val links: MutableMap<String, IPeripheral> =
            LinkLuaApi.Companion.available((this as IComputerAccess).mainThreadMonitor)
        if (links.isEmpty()) return
        val result = LinkedHashMap<String, IPeripheral>(cir.returnValue)
        result.putAll(links)
        cir.setReturnValue(result)
    }

    @Inject(method = ["getAvailablePeripheral"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$one`(name: String, cir: CallbackInfoReturnable<IPeripheral>) {
        val link: IPeripheral? =
            LinkLuaApi.Companion.available((this as IComputerAccess).mainThreadMonitor).get(name)
        if (link != null) cir.setReturnValue(link)
    }
}
