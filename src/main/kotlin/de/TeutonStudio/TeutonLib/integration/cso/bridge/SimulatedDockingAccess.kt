package de.TeutonStudio.TeutonLib.integration.cso.bridge

import de.TeutonStudio.TeutonLib.TeutonLib
import net.minecraft.world.level.block.entity.BlockEntity
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Reflection confines the binary Simulated docking API to one small boundary. */
object SimulatedDockingAccess {
    private data class Accessors(val other: Method, val locked: Method)
    private val cache = ConcurrentHashMap<Class<*>, Accessors>()
    fun other(connector: BlockEntity): BlockEntity? = try { accessors(connector).other.invoke(connector) as? BlockEntity }
    catch (ex: ReflectiveOperationException) { TeutonLib.LOGGER.error("Failed to query Simulated docking peer", ex); null }
    fun locked(connector: BlockEntity): Boolean = try { accessors(connector).locked.invoke(connector) == true }
    catch (ex: ReflectiveOperationException) { TeutonLib.LOGGER.error("Failed to query Simulated docking lock state", ex); false }
    private fun accessors(connector: BlockEntity) = cache.computeIfAbsent(connector.javaClass) { type ->
        try { Accessors(type.getMethod("getOtherConnector"), type.getMethod("isLocked")) }
        catch (ex: ReflectiveOperationException) { throw IllegalStateException("Unsupported Simulated DockingConnectorBlockEntity API: ${type.name}", ex) }
    }
}
