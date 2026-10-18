@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package de.TeutonStudio.TeutonLib.integration.peripheral

import dan200.computercraft.api.lua.*
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IPeripheral
import de.teutonstudio.ccaeroworks.computer.PeripheralBinding
import de.teutonstudio.ccaeroworks.computer.PeripheralLuaHandle
import java.util.LinkedHashMap

/** Views onto the console runtime's existing attachments: never calls attach a second time.  */
internal object ConsoleLinkDevices {
    @JvmStatic
    fun devices(bindings: Map<String, PeripheralBinding>): MutableMap<String, LinkDevice> {
        val result = LinkedHashMap<String, LinkDevice>()
        bindings.forEach { (name: String, binding: PeripheralBinding) ->
            if (!name!!.startsWith("cc_aeroworks:link_")) return@forEach
            result.put(name, object : LinkDevice {
                override fun peripheral(): IPeripheral {
                    return binding!!.node.target
                }

                override fun methods(): Set<String> {
                    return binding!!.methodNames
                }

                @Throws(LuaException::class)
                override fun call(context: ILuaContext, method: String, arguments: IArguments): MethodResult {
                    return binding!!.call(context, method, arguments)
                }

                override fun handle(): Any {
                    return PeripheralLuaHandle(binding)
                }

                override fun info(): Map<String, Any?> {
                    return binding!!.info()
                }
            })
        }
        return result
    }
}
