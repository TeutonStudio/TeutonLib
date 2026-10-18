package de.TeutonStudio.TeutonLib.integration.peripheral

import dan200.computercraft.api.lua.*
import dan200.computercraft.api.peripheral.IPeripheral

interface LinkDevice {
    fun peripheral(): IPeripheral
    fun methods(): Set<String>
    @Throws(LuaException::class)
    fun call(context: ILuaContext, method: String, arguments: IArguments): MethodResult
    fun handle(): Any
    fun info(): Map<String, Any?>
    fun close() {}
}
