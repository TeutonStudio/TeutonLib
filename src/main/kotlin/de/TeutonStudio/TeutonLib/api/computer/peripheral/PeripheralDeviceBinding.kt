package de.TeutonStudio.TeutonLib.api.computer.peripheral

import dan200.computercraft.api.filesystem.Mount
import dan200.computercraft.api.filesystem.WritableMount
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
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

/** One idempotent CC:Tweaked attachment, including method dispatch and owned mounts. */
class PeripheralDeviceBinding internal constructor(
    private val system: IComputerSystem,
    endpoint: PeripheralEndpoint,
    private val available: () -> Map<String, IPeripheral>,
    private val describe: (PeripheralEndpoint) -> Map<String, Any>
) : IComputerAccess, GuardedLuaContext.Guard {
    var endpoint: PeripheralEndpoint = endpoint
        private set
    private val methods: Map<String, PeripheralMethod> =
        ServerContext.get(system.level.server).peripheralMethods().getSelfMethods(endpoint.peripheral)
    private val mounts = linkedSetOf<String>()
    private var attached = false
    private var contextWrapper: GuardedLuaContext? = null

    val methodNames: Set<String> get() = methods.keys

    @Synchronized
    internal fun attach() {
        if (attached) return
        attached = true
        try {
            endpoint.peripheral.attach(this)
        } catch (throwable: Throwable) {
            runCatching { endpoint.peripheral.detach(this) }.exceptionOrNull()?.let(throwable::addSuppressed)
            cleanupMounts(throwable)
            attached = false
            contextWrapper = null
            throw throwable
        }
    }

    @Synchronized
    internal fun update(next: PeripheralEndpoint) {
        checkAttached()
        endpoint = next
    }

    fun call(context: ILuaContext, name: String, arguments: IArguments): MethodResult {
        val method = methods[name] ?: throw LuaException("No such method $name")
        val guarded = synchronized(this) {
            checkAttached()
            contextWrapper?.takeIf { it.wraps(context) }
                ?: GuardedLuaContext(context, this).also { contextWrapper = it }
        }
        return method.apply(endpoint.peripheral, guarded, this, arguments).adjustError(1)
    }

    fun info(): Map<String, Any> { checkAttached(); return describe(endpoint) }

    @Synchronized
    fun close() {
        if (!attached && mounts.isEmpty()) { contextWrapper = null; return }
        var failure = if (attached) runCatching { endpoint.peripheral.detach(this) }.exceptionOrNull() else null
        failure = cleanupMounts(failure)
        attached = false
        contextWrapper = null
        failure?.let { throw it }
    }

    override fun checkValid(): Boolean = synchronized(this) { attached }
    @Synchronized override fun mount(desiredLocation: String, mount: Mount, driveName: String): String? {
        checkAttached(); return system.mount(desiredLocation, mount, driveName)?.also(mounts::add)
    }
    @Synchronized override fun mountWritable(desiredLocation: String, mount: WritableMount, driveName: String): String? {
        checkAttached(); return system.mountWritable(desiredLocation, mount, driveName)?.also(mounts::add)
    }
    @Synchronized override fun unmount(location: String?) {
        checkAttached(); system.unmount(location); if (location != null) mounts.remove(location)
    }
    override fun getID(): Int { checkAttached(); return system.id }
    override fun queueEvent(event: String, vararg arguments: Any?) { checkAttached(); system.queueEvent(event, *arguments) }
    override fun getAttachmentName(): String { checkAttached(); return endpoint.address }
    override fun getAvailablePeripherals(): Map<String, IPeripheral> {
        checkAttached(); return linkedMapOf<String, IPeripheral>().apply { putAll(system.availablePeripherals); putAll(available()) }
    }
    override fun getAvailablePeripheral(name: String): IPeripheral? {
        checkAttached(); return available()[name] ?: system.getAvailablePeripheral(name)
    }
    override fun getMainThreadMonitor(): WorkMonitor { checkAttached(); return system.mainThreadMonitor }

    private fun cleanupMounts(primary: Throwable?): Throwable? {
        var failure = primary
        val owned = mounts.toList().also { mounts.clear() }
        owned.forEach { location ->
            val unmountFailure = runCatching { system.unmount(location) }.exceptionOrNull() ?: return@forEach
            if (failure == null) failure = unmountFailure else if (failure !== unmountFailure) failure!!.addSuppressed(unmountFailure)
        }
        return failure
    }

    @Synchronized
    private fun checkAttached() { if (!attached) throw NotAttachedException() }
}
