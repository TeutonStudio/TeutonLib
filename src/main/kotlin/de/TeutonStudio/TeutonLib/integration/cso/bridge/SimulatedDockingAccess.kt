package de.TeutonStudio.TeutonLib.integration.cso.bridge

import de.TeutonStudio.TeutonLib.TeutonLib
import net.minecraft.world.level.block.entity.BlockEntity
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.registries.BuiltInRegistries
import net.neoforged.fml.ModList

/** Reflection confines the binary Simulated docking API to one small boundary. */
object SimulatedDockingAccess {
    private const val CONNECTOR_CLASS =
        "dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity"
    private data class Accessors(
        val other: Method,
        val locked: Method,
        val extended: Method,
        val retracted: Method,
        val hasOther: Method
    )
    private val cache = ConcurrentHashMap<Class<*>, Accessors>()
    private val connectorType: Class<*>? by lazy {
        if (!ModList.get().isLoaded("simulated")) null else runCatching { Class.forName(CONNECTOR_CLASS) }
            .onFailure { TeutonLib.LOGGER.error("Unsupported Simulated docking connector", it) }.getOrNull()
    }
    fun other(connector: BlockEntity): BlockEntity? = try { accessors(connector).other.invoke(connector) as? BlockEntity }
    catch (ex: ReflectiveOperationException) { TeutonLib.LOGGER.error("Failed to query Simulated docking peer", ex); null }
    fun locked(connector: BlockEntity): Boolean = try { accessors(connector).locked.invoke(connector) == true }
    catch (ex: ReflectiveOperationException) { TeutonLib.LOGGER.error("Failed to query Simulated docking lock state", ex); false }
    fun extended(connector: BlockEntity): Boolean = boolean(connector) { it.extended }
    fun retracted(connector: BlockEntity): Boolean = boolean(connector) { it.retracted }
    fun hasOther(connector: BlockEntity): Boolean = boolean(connector) { it.hasOther }
    fun available(): Boolean = connectorType != null
    fun isConnector(blockEntity: BlockEntity): Boolean =
        connectorType?.isInstance(blockEntity) == true ||
            BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.type).toString() == "simulated:docking_connector"

    private fun boolean(connector: BlockEntity, method: (Accessors) -> Method): Boolean = try {
        method(accessors(connector)).invoke(connector) == true
    } catch (ex: ReflectiveOperationException) {
        TeutonLib.LOGGER.error("Failed to query Simulated docking state", ex); false
    }
    private fun accessors(connector: BlockEntity) = cache.computeIfAbsent(connector.javaClass) { type ->
        try { Accessors(
            type.getMethod("getOtherConnector"), type.getMethod("isLocked"), type.getMethod("isExtended"),
            type.getMethod("isRetracted"), type.getMethod("hasOtherConnector")
        ) }
        catch (ex: ReflectiveOperationException) { throw IllegalStateException("Unsupported Simulated DockingConnectorBlockEntity API: ${type.name}", ex) }
    }
}
