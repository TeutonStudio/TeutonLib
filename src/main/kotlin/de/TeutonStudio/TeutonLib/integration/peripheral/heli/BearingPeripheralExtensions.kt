package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.LuaException
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkDevice
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkExtensionMethod

internal object BearingPeripheralExtensions {
    fun methods(device: LinkDevice): Map<String, LinkExtensionMethod> {
        val peripheral = device.peripheral()
        if (peripheral !is GyroscopicBearingPeripheral) return emptyMap()
        val block = peripheral.blockEntity
        return mapOf(
            "adjustTilt" to LinkExtensionMethod { context, arguments ->
                val values = table(arguments)
                context.executeMainThreadTask {
                    device.info()
                    BearingControl.adjustTilt(block, values)
                    emptyArray()
                }
            },
            "setTargetDirection" to LinkExtensionMethod { context, arguments ->
                val values = table(arguments)
                context.executeMainThreadTask {
                    device.info()
                    BearingControl.setTargetDirection(block, values)
                    emptyArray()
                }
            }
        ).filterKeys { it !in device.methods() }
    }

    @Throws(LuaException::class)
    private fun table(arguments: IArguments): Map<*, *> {
        if (arguments.count() != 1) throw LuaException("Expected one table")
        return arguments.getTable(0)
    }
}
