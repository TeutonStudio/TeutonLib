package de.TeutonStudio.TeutonLib.integration.peripheral

import dan200.computercraft.api.component.ComputerComponents
import dan200.computercraft.api.lua.*
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.WorkMonitor
import dan200.computercraft.api.pocket.IPocketAccess
import dan200.computercraft.api.turtle.ITurtleAccess
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import de.TeutonStudio.TeutonLib.TeutonLib
import java.lang.RuntimeException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.function.Consumer
import kotlin.concurrent.Volatile
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity

class LinkLuaApi(private val system: IComputerSystem) : ILuaAPI {
    @Volatile
    private var owner: BlockEntity? = null
    private val portable: Boolean

    @Volatile
    private var devices: MutableMap<String, LinkDevice> = java.util.Map.of<String, LinkDevice>()

    @Volatile
    private var peripherals: MutableMap<String, IPeripheral> = java.util.Map.of<String, IPeripheral>()

    @Volatile
    private var running = false

    init {
        portable =
            system.getComponent<IPocketAccess>(ComputerComponents.POCKET) != null || system.getComponent<ITurtleAccess>(
                ComputerComponents.TURTLE
            ) != null
    }

    override fun getNames(): Array<String> {
        return arrayOf<String>("teutonlib_links")
    }

    override fun getModuleName(): String {
        return "teutonlib.links"
    }

    override fun startup() {
        running = true
        ACCESS.put(system.mainThreadMonitor, this)
        LinkRegistry.register(this)
    }

    @Synchronized
    override fun shutdown() {
        running = false
        LinkRegistry.remove(this)
        ACCESS.remove(system.mainThreadMonitor, this)
        for (device in devices.values) close(device)
        devices = java.util.Map.of<String, LinkDevice>()
        peripherals = java.util.Map.of<String, IPeripheral>()
    }

    fun available(): MutableMap<String, IPeripheral> {
        if (system.level.server.isSameThread()) LinkRegistry.validate()
        val result = LinkedHashMap<String, IPeripheral>()
        peripherals.forEach { (name: String, peripheral: IPeripheral) ->
            val current = LinkRegistry.connection(name)
            if (current != null && current.owner === owner && LinkBinding.Companion.equivalent(
                    current.peripheral,
                    peripheral
                )
            ) result.put(name, peripheral)
        }
        return result
    }

    @Synchronized
    fun refresh() {
        if (!running) return
        // CC intentionally denies spatial access while its API factories run.
        // Resolve the live host only after activation, on the server thread.
        val host = LinkRegistry.blockAt(system.level, system.position)
        owner = if (!portable && LinkRegistry.isTarget(host)) host else null
        if (owner == null) {
            devices.values.forEach(Consumer { device: LinkDevice? -> Companion.close(device!!) })
            publish(java.util.Map.of<String, LinkDevice>())
            return
        }
        val console = LinkRegistry.console(system)
        if (console != null) {
            val next = console.`teutonlib$devices`()
            publish(next)
            return
        }
        if (owner !is ComputerBlockEntity) {
            publish(java.util.Map.of<String, LinkDevice>())
            return
        }
        val next: MutableMap<String, LinkDevice> = LinkedHashMap<String, LinkDevice>()
        for (connection in LinkRegistry.connections(owner)) {
            val previous = devices.get(connection.name())
            if (previous is LinkBinding && previous.matches(connection) ||
                previous is ExtendedLinkDevice && previous.matches(connection)
            ) next.put(connection.name(), previous)
            else next.put(connection.name(), PeripheralExtensions.augment(LinkBinding(this, system, connection)))
        }
        for (entry in devices.entries) if (next.get(entry.key) !== entry.value) {
            close(entry.value)
            system.queueEvent("peripheral_detach", entry.key)
        }
        val old = devices
        publish(next)
        for (entry in next.entries) if (old.get(entry.key) !== entry.value) {
            try {
                when (val device = entry.value) {
                    is LinkBinding -> device.attach()
                    is ExtendedLinkDevice -> device.attachDelegate()
                    else -> error("Unexpected link device ${device.javaClass.name}")
                }
                system.queueEvent("peripheral", entry.key)
            } catch (failure: RuntimeException) {
                TeutonLib.Companion.LOGGER.warn("Could not attach Peripheral Link {}", entry.key, failure)
                val working = LinkedHashMap<String, LinkDevice>(devices)
                working.remove(entry.key)
                publish(working)
            }
        }
    }

    private fun publish(next: MutableMap<String, LinkDevice>) {
        devices = Collections.unmodifiableMap<String, LinkDevice>(LinkedHashMap<String, LinkDevice>(next))
        val available = LinkedHashMap<String, IPeripheral>()
        next.forEach { (name: String, device: LinkDevice?) -> available.put(name, device!!.peripheral()) }
        peripherals = Collections.unmodifiableMap<String, IPeripheral>(available)
    }

    @Throws(LuaException::class)
    private fun device(name: String?): LinkDevice {
        val device = devices[name] ?: throw LuaException("Peripheral '$name' is no longer attached")
        return device
    }

    @LuaFunction("getNames")
    fun names(): MutableList<String> {
        return java.util.List.copyOf<String>(devices.keys)
    }

    @LuaFunction
    fun isPresent(name: String?): Boolean {
        return devices.containsKey(name)
    }

    @LuaFunction
    fun getType(name: String?): MethodResult {
        val device = devices.get(name)
        if (device == null) return MethodResult.of(null as Any?)
        val types = LinkedHashSet<String>()
        types.add(device.peripheral().type)
        types.addAll(device.peripheral().additionalTypes)
        return MethodResult.of(*types.toTypedArray())
    }

    @LuaFunction
    @Throws(LuaException::class)
    fun getMethods(name: String?): MutableList<String> {
        return device(name).methods().stream().sorted().toList()
    }

    @LuaFunction
    @Throws(LuaException::class)
    fun wrap(name: String?): Any? {
        return device(name).handle()
    }

    @LuaFunction
    @Throws(LuaException::class)
    fun call(context: ILuaContext, arguments: IArguments): MethodResult? {
        return device(arguments.getString(0)).call(context, arguments.getString(1), arguments.drop(2))
    }

    companion object {
        private val ACCESS: MutableMap<WorkMonitor, LinkLuaApi> =
            Collections.synchronizedMap<WorkMonitor, LinkLuaApi>(IdentityHashMap<WorkMonitor, LinkLuaApi>())

        @JvmStatic
        fun available(monitor: WorkMonitor?): MutableMap<String, IPeripheral> {
            val api: LinkLuaApi? = ACCESS.get(monitor)
            return if (api == null) java.util.Map.of<String, IPeripheral>() else api.available()
        }

        @JvmStatic
        private fun close(device: LinkDevice) {
            try {
                device.close()
            } catch (failure: RuntimeException) {
                TeutonLib.Companion.LOGGER.warn("Could not detach Peripheral Link", failure)
            }
        }

        @JvmStatic
        fun describe(connection: LinkRegistry.Connection): MutableMap<String, Any?> {
            val dimension = connection.link.level!!.dimension().location().toString()
            val result = LinkedHashMap<String, Any?>()
            result.put("address", connection.name())
            result.put("connectionType", "wireless")
            result.put("linkPosition", position(connection.link.blockPos, dimension))
            result.put("targetPosition", position(connection.link.devicePosition(), dimension))
            result.put("targetId", connection.link.target().toString())
            return result
        }

        @JvmStatic
        private fun position(pos: BlockPos, dimension: String): MutableMap<String, Any?> {
            return java.util.Map.of<String, Any?>("x", pos.x, "y", pos.y, "z", pos.z, "dimension", dimension)
        }
    }
}
