package de.TeutonStudio.TeutonLib.api.computer.peripheral

import dan200.computercraft.api.peripheral.IPeripheral
import de.TeutonStudio.TeutonLib.api.computer.ComputerContext

/** Generic host in an addon-defined peripheral topology. Identifiers are opaque to TeutonLib. */
data class PeripheralHost(val key: String, val address: String, val metadata: Map<String, Any?> = emptyMap())

/** Generic attachable endpoint. [descriptor] remains owned and interpreted by the provider. */
data class PeripheralEndpoint(
    val address: String,
    val primaryType: String,
    val types: Set<String>,
    val peripheral: IPeripheral,
    val ownerKey: String,
    val descriptor: Any? = null,
    val metadata: Map<String, Any?> = emptyMap()
)

/** Immutable point-in-time graph supplied by an addon. */
data class PeripheralTopology(
    val revision: Long,
    val state: String,
    val hosts: List<PeripheralHost>,
    val endpoints: List<PeripheralEndpoint>,
    val metadata: Map<String, Any?> = emptyMap()
)

fun interface PeripheralTopologyProvider {
    fun snapshot(computer: ComputerContext): PeripheralTopology
}
