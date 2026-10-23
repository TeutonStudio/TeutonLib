package de.TeutonStudio.TeutonLib.api.computer.peripheral

import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.WorkMonitor
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.ComputerContext
import java.util.Collections
import java.util.IdentityHashMap

/** Generic graph reconciliation and exactly-once attach/detach lifecycle. */
class PeripheralAttachmentRuntime(
    private val system: IComputerSystem,
    private val context: () -> ComputerContext,
    private val provider: PeripheralTopologyProvider,
    private val describe: (PeripheralEndpoint) -> Map<String, Any> = { endpoint ->
        linkedMapOf<String, Any>(
            "address" to endpoint.address,
            "type" to endpoint.primaryType,
            "types" to endpoint.types.toList()
        ).apply { endpoint.metadata.forEach { (key, value) -> if (value != null) put(key, value) } }
    },
    private val onAttached: (PeripheralEndpoint) -> Unit = {},
    private val onDetached: (PeripheralEndpoint) -> Unit = {}
) : AutoCloseable {
    private var topology: PeripheralTopology? = null
    private val bindings = linkedMapOf<String, PeripheralDeviceBinding>()
    private var initialized = false
    private var lastScanTick = Long.MIN_VALUE

    init {
        register(system.mainThreadMonitor, this)
    }

    @Synchronized
    fun refresh(force: Boolean = false): PeripheralTopology {
        val computer = context()
        val tick = computer.level().gameTime
        if (!force && topology != null && lastScanTick == tick) return topology!!
        val next = provider.snapshot(computer)
        lastScanTick = tick
        require(next.endpoints.map { it.address }.toSet().size == next.endpoints.size) {
            "Peripheral topology contains duplicate endpoint addresses"
        }
        val desired = next.endpoints.associateBy { it.address }
        val detached = mutableListOf<PeripheralEndpoint>()
        val iterator = bindings.iterator()
        while (iterator.hasNext()) {
            val (address, binding) = iterator.next()
            val endpoint = desired[address]
            if (endpoint == null || !equivalent(binding.endpoint, endpoint)) {
                detached += binding.endpoint
                binding.close()
                iterator.remove()
            } else {
                binding.update(endpoint)
            }
        }
        topology = next
        val attached = mutableListOf<PeripheralEndpoint>()
        try {
            next.endpoints.forEach { endpoint ->
                if (endpoint.address in bindings) return@forEach
                val binding = PeripheralDeviceBinding(system, endpoint, ::availablePeripherals, describe)
                bindings[endpoint.address] = binding
                binding.attach()
                attached += endpoint
            }
        } catch (throwable: Throwable) {
            runCatching { invalidate(false) }.exceptionOrNull()?.let(throwable::addSuppressed)
            throw throwable
        }
        if (initialized) {
            detached.forEach(onDetached)
            attached.forEach(onAttached)
        }
        initialized = true
        return next
    }

    @Synchronized
    fun binding(address: String): PeripheralDeviceBinding = bindings[address]
        ?: throw LuaException("Peripheral '$address' is no longer attached")

    @Synchronized
    fun availablePeripherals(): Map<String, IPeripheral> =
        bindings.mapValuesTo(linkedMapOf()) { it.value.endpoint.peripheral }

    @Synchronized
    fun bindingSnapshot(): Map<String, PeripheralDeviceBinding> = LinkedHashMap(bindings)

    @Synchronized
    fun invalidate(queueEvents: Boolean = true) {
        val detached = bindings.values.map { it.endpoint }
        var failure: Throwable? = null
        bindings.values.forEach { binding ->
            val closeFailure = runCatching { binding.close() }.exceptionOrNull() ?: return@forEach
            if (failure == null) failure = closeFailure else if (failure !== closeFailure) failure!!.addSuppressed(closeFailure)
        }
        bindings.clear()
        topology = null
        lastScanTick = Long.MIN_VALUE
        if (queueEvents && initialized) detached.forEach(onDetached)
        failure?.let { throw it }
    }

    @Synchronized
    override fun close() {
        try { invalidate(false) } finally {
            initialized = false
            unregister(system.mainThreadMonitor, this)
        }
    }

    private fun equivalent(first: PeripheralEndpoint, second: PeripheralEndpoint): Boolean =
        first.primaryType == second.primaryType && first.types == second.types && first.ownerKey == second.ownerKey &&
            (first.peripheral === second.peripheral || runCatching {
                first.peripheral == second.peripheral && second.peripheral == first.peripheral
            }.getOrDefault(false))

    companion object {
        private val active = IdentityHashMap<WorkMonitor, MutableSet<PeripheralAttachmentRuntime>>()

        @Synchronized
        private fun register(monitor: WorkMonitor, runtime: PeripheralAttachmentRuntime) {
            active.getOrPut(monitor) {
                Collections.newSetFromMap(IdentityHashMap<PeripheralAttachmentRuntime, Boolean>())
            }.add(runtime)
        }

        @Synchronized
        private fun unregister(monitor: WorkMonitor, runtime: PeripheralAttachmentRuntime) {
            active[monitor]?.let { runtimes ->
                runtimes.remove(runtime)
                if (runtimes.isEmpty()) active.remove(monitor)
            }
        }

        @JvmStatic
        @Synchronized
        fun bindings(monitor: WorkMonitor): Map<String, PeripheralDeviceBinding> = buildMap {
            active[monitor].orEmpty().forEach { runtime ->
                runtime.bindingSnapshot().forEach { (address, binding) -> putIfAbsent(address, binding) }
            }
        }

        /** Reconcile registered runtimes immediately after an external topology source changes. */
        @JvmStatic
        internal fun reconcileAll() {
            val runtimes = synchronized(this) { active.values.flatMap { it.toList() } }
            reconcile(runtimes)
        }

        @JvmStatic
        internal fun reconcile(monitor: WorkMonitor) {
            val runtimes = synchronized(this) { active[monitor]?.toList().orEmpty() }
            reconcile(runtimes)
        }

        private fun reconcile(runtimes: List<PeripheralAttachmentRuntime>) {
            runtimes.forEach { runtime ->
                runCatching { runtime.refresh(true) }.onFailure { failure ->
                    TeutonLib.LOGGER.warn("Could not reconcile peripheral topology", failure)
                }
            }
        }
    }
}
