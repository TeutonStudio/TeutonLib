package de.TeutonStudio.TeutonLib.mixin.peripheral.console;

import dan200.computercraft.api.lua.ILuaAPI;
import de.TeutonStudio.TeutonLib.integration.peripheral.ConsoleRuntimeAccess;
import de.teutonstudio.ccaeroworks.computer.ComputerConsoleLuaApi;
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkRuntime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** CC reuses API instances on reboot. Re-register the same runtime on each boot,
 * and leave switched-off computers detached from remote peripherals. */
@Mixin(value = ComputerConsoleLuaApi.class, remap = false)
public abstract class ConsoleApiLifecycleMixin implements ILuaAPI {
    @Shadow @Final private PeripheralNetworkRuntime runtime;
    @Override public void startup() { ((ConsoleRuntimeAccess) (Object) runtime).teutonlib$activate(); }
}
