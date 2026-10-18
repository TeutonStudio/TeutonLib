@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package de.TeutonStudio.TeutonLib.mixin.peripheral.console

import dan200.computercraft.api.lua.ILuaAPI
import de.TeutonStudio.TeutonLib.integration.peripheral.ConsoleRuntimeAccess
import de.teutonstudio.ccaeroworks.computer.ComputerConsoleLuaApi
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkRuntime
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

/** CC reuses API instances on reboot. Re-register the same runtime on each boot,
 * and leave switched-off computers detached from remote peripherals.  */
@Mixin(value = [ComputerConsoleLuaApi::class], remap = false)
abstract class ConsoleApiLifecycleMixin : ILuaAPI {
    @Shadow
    @Final
    private val runtime: PeripheralNetworkRuntime? = null
    override fun startup() {
        (runtime as Any? as ConsoleRuntimeAccess).`teutonlib$activate`()
    }
}
