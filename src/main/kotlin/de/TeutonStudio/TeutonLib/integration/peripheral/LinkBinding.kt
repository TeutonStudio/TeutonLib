package de.TeutonStudio.TeutonLib.integration.peripheral

import dan200.computercraft.api.filesystem.Mount
import dan200.computercraft.api.filesystem.WritableMount
import dan200.computercraft.api.lua.*
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.IDynamicLuaObject
import dan200.computercraft.api.lua.ILuaCallback
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.NotAttachedException
import dan200.computercraft.api.peripheral.WorkMonitor
import dan200.computercraft.core.computer.GuardedLuaContext
import dan200.computercraft.core.methods.PeripheralMethod
import dan200.computercraft.shared.computer.core.ServerContext
import java.lang.RuntimeException
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.function.IntFunction
import kotlin.concurrent.Volatile

/** One real CC attachment, with its own mount ownership and irreversible handle lifetime.  */
class LinkBinding(
    private val api: LinkLuaApi,
    private val system: IComputerSystem,
    private val connection: LinkRegistry.Connection
) : LinkDevice, IComputerAccess, GuardedLuaContext.Guard {
    private val methods: MutableMap<String, PeripheralMethod>
    private val mounts: MutableSet<String> = HashSet<String>()

    @Volatile
    private var attached = false

    @Volatile
    private var invalidated = false

    @Volatile
    private var closing = false

    init {
        methods =
            ServerContext.get(system.level.server).peripheralMethods().getSelfMethods(connection.peripheral)
    }

    fun attach() {
        attached = true
        try {
            peripheral()!!.attach(this)
        } catch (failure: Throwable) {
            try {
                close()
            } catch (cleanup: Throwable) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    fun matches(other: LinkRegistry.Connection): Boolean {
        return !invalidated && other.owner === connection.owner && other.anchor === connection.anchor && Companion.equivalent(
            peripheral()!!,
            other.peripheral
        )
    }

    override fun peripheral(): IPeripheral {
        return connection.peripheral
    }

    override fun methods(): MutableSet<String> {
        return methods.keys
    }

    override fun checkValid(): Boolean {
        if (closing) return attached
        if (!attached || invalidated) return closing && attached
        if (system.level.server.isSameThread()) LinkRegistry.validate()
        val current = connection.link.connection
        if (current == null || !matches(current)) invalidated = true
        return !invalidated
    }

    private fun check() {
        if (!checkValid()) throw NotAttachedException()
    }

    @Throws(LuaException::class)
    private fun checkLua() {
        if (!checkValid()) throw LuaException("Peripheral is no longer attached")
    }

    @Throws(LuaException::class)
    override fun call(context: ILuaContext, method: String, arguments: IArguments): MethodResult {
        checkLua()
        val implementation = methods[method] ?: throw LuaException("No such method $method")
        return guard(
            implementation.apply(peripheral(), GuardedLuaContext(context, this), this, arguments).adjustError(1)
        )
    }

    private fun guard(result: MethodResult): MethodResult {
        if (result.callback == null) return result
        return MethodResult.yield(result.result, ILuaCallback { arguments: Array<Any?> ->
            checkLua()
            guard(result.callback!!.resume(arguments))
        }).adjustError(result.errorAdjust)
    }

    override fun handle(): Any {
        val names: MutableList<String> = ArrayList<String>(methods.keys)
        Collections.sort<String>(names)
        if (!names.contains("getPeripheralInfo")) names.add("getPeripheralInfo")
        val methodNames: Array<String> = names.toTypedArray()
        return object : IDynamicLuaObject {
            override fun getMethodNames(): Array<String> {
                return methodNames
            }

            @Throws(LuaException::class)
            override fun callMethod(context: ILuaContext, index: Int, args: IArguments): MethodResult {
                checkLua()
                if (index < 0 || index >= methodNames.size) throw LuaException("Unknown method")
                val name = methodNames[index]
                return if (name == "getPeripheralInfo" && !methods.containsKey(name))
                    MethodResult.of(info())
                else
                    call(context, name, args)
            }
        }
    }

    override fun info(): MutableMap<String, Any?> {
        check()
        return LinkLuaApi.Companion.describe(connection)
    }

    @Synchronized
    override fun close() {
        if (!attached && mounts.isEmpty()) return
        // detach may legitimately unmount, so leave the access active during this callback.
        var failure: RuntimeException? = null
        closing = true
        try {
            if (attached) peripheral()!!.detach(this)
        } catch (exception: RuntimeException) {
            failure = exception
        } finally {
            attached = false
            invalidated = true
            closing = false
            for (path in java.util.List.copyOf<String>(mounts)) {
                try {
                    system.unmount(path)
                } catch (exception: RuntimeException) {
                    if (failure == null) failure = exception else failure.addSuppressed(exception)
                }
            }
            mounts.clear()
        }
        if (failure != null) throw failure
    }

    @Synchronized
    override fun mount(desired: String, mount: Mount, drive: String): String? {
        check()
        val actual = system.mount(desired, mount, drive)
        if (actual != null) mounts.add(actual)
        return actual
    }

    @Synchronized
    override fun mountWritable(desired: String, mount: WritableMount, drive: String): String? {
        check()
        val actual = system.mountWritable(desired, mount, drive)
        if (actual != null) mounts.add(actual)
        return actual
    }

    @Synchronized
    override fun unmount(path: String?) {
        check()
        if (mounts.remove(path)) system.unmount(path)
    }

    override fun getID(): Int {
        check()
        return system.id
    }

    override fun queueEvent(event: String, vararg args: Any?) {
        check()
        system.queueEvent(event, *args)
    }

    override fun getAttachmentName(): String {
        check()
        return connection.name()
    }

    override fun getAvailablePeripherals(): MutableMap<String, IPeripheral> {
        check()
        val available = HashMap<String, IPeripheral>(system.availablePeripherals)
        available.putAll(api.available())
        return available
    }

    override fun getAvailablePeripheral(name: String): IPeripheral? {
        check()
        val result = api.available().get(name)
        return if (result == null) system.getAvailablePeripheral(name) else result
    }

    override fun getMainThreadMonitor(): WorkMonitor {
        check()
        return system.mainThreadMonitor
    }

    companion object {
        @JvmStatic
        fun equivalent(first: IPeripheral, second: IPeripheral): Boolean {
            return first === second || first.equals(second) && second.equals(first)
        }
    }
}
