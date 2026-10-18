package de.TeutonStudio.TeutonLib.mixin.peripheral

import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.IComputerAccess
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkLuaApi
import net.neoforged.fml.ModList
import dan200.computercraft.core.apis.IAPIEnvironment
import dan200.computercraft.core.computer.ComputerSide
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemPeripheral
import java.util.LinkedHashMap
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** CC's API-factory access normally exposes no peripherals at all. Supply the
 * already-connected sides and their wired networks without attaching anything.  */
@Mixin(targets = ["dan200.computercraft.shared.computer.core.ComputerSystem"], remap = false)
abstract class LinkSystemAccessMixin {
    @Shadow
    @Final
    private val environment: IAPIEnvironment? = null

    @Unique
    private fun `teutonlib$reachable`(): MutableMap<String, IPeripheral> {
        val result = LinkedHashMap<String, IPeripheral>()
        for (side in ComputerSide.entries) {
            val peripheral = environment!!.getPeripheral(side)
            if (peripheral == null) continue
            result.put(side.getName(), peripheral)
            val element = (peripheral as? WiredModemPeripheral)?.node?.element as? WiredModemElement
            if (element != null) result.putAll(element.remotePeripherals)
        }
        if (ModList.get().isLoaded("create"))
            result.putAll(LinkLuaApi.available((this as IComputerAccess).mainThreadMonitor))
        return result
    }

    @Inject(method = ["getAvailablePeripherals"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$all`(cir: CallbackInfoReturnable<MutableMap<String, IPeripheral>>) {
        val result = LinkedHashMap<String, IPeripheral>(cir.returnValue)
        result.putAll(`teutonlib$reachable`())
        cir.setReturnValue(result)
    }

    @Inject(method = ["getAvailablePeripheral"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$one`(name: String, cir: CallbackInfoReturnable<IPeripheral>) {
        if (cir.returnValue == null) cir.setReturnValue(`teutonlib$reachable`().get(name))
    }
}
