package de.TeutonStudio.TeutonLib.integration.peripheral

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IDynamicLuaObject
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IPeripheral
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.peripheral.heli.HeliPeripheralExtensions
import java.util.TreeSet
import net.neoforged.fml.ModList

internal fun interface LinkExtensionMethod {
    @Throws(LuaException::class)
    fun call(context: ILuaContext, arguments: IArguments): MethodResult
}

internal object PeripheralExtensions {
    fun augment(device: LinkDevice): LinkDevice {
        if (!ModList.get().isLoaded("sable")) return device
        val additions = HeliPeripheralExtensions.methods(device)
        if (additions.isEmpty()) return device
        val collisions = additions.keys.intersect(device.methods())
        check(collisions.isEmpty()) {
            "Peripheral ${device.peripheral().type} already implements TeutonLib method(s): ${collisions.sorted().joinToString()}"
        }
        TeutonLib.LOGGER.debug(
            "Extending Peripheral Link type {} with {}",
            device.peripheral().type,
            additions.keys.sorted().joinToString()
        )
        return ExtendedLinkDevice(device, additions)
    }
}

internal class ExtendedLinkDevice(
    private val delegate: LinkDevice,
    private val additions: Map<String, LinkExtensionMethod>
) : LinkDevice {
    init {
        val collisions = additions.keys.intersect(delegate.methods())
        require(collisions.isEmpty()) {
            "Peripheral ${delegate.peripheral().type} already implements TeutonLib method(s): ${collisions.sorted().joinToString()}"
        }
    }

    private val names = (delegate.methods() + additions.keys).toSortedSet()

    override fun peripheral(): IPeripheral = delegate.peripheral()
    override fun methods(): Set<String> = names

    override fun call(context: ILuaContext, method: String, arguments: IArguments): MethodResult {
        val addition = additions[method]
        return if (addition == null) delegate.call(context, method, arguments) else addition.call(context, arguments)
    }

    override fun handle(): Any {
        val handleNames = TreeSet(names)
        handleNames.add("getPeripheralInfo")
        val ordered = handleNames.toTypedArray()
        return object : IDynamicLuaObject {
            override fun getMethodNames(): Array<String> = ordered

            override fun callMethod(context: ILuaContext, index: Int, args: IArguments): MethodResult {
                if (index !in ordered.indices) throw LuaException("Unknown method")
                return if (ordered[index] == "getPeripheralInfo" && ordered[index] !in names) {
                    MethodResult.of(info())
                } else {
                    call(context, ordered[index], args)
                }
            }
        }
    }

    override fun info(): Map<String, Any?> = delegate.info()
    override fun close() = delegate.close()

    fun matches(connection: LinkRegistry.Connection): Boolean =
        delegate is LinkBinding && delegate.matches(connection)

    fun attachDelegate() {
        (delegate as LinkBinding).attach()
    }
}
