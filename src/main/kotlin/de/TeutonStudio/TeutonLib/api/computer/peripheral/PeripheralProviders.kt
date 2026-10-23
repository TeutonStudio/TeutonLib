package de.TeutonStudio.TeutonLib.api.computer.peripheral

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.ComputerContext
import java.util.concurrent.CopyOnWriteArrayList
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * Describes an addon-owned link target and resolves its currently active computer owner.
 *
 * [supports] must remain true while a valid target is temporarily inactive (for example while its
 * removable computer is absent). [owner] may return null during that state.
 */
fun interface PeripheralTargetProvider {
    fun owner(anchor: BlockEntity): BlockEntity?

    fun supports(anchor: BlockEntity): Boolean = owner(anchor) != null
}

/** Supplies addon-independent endpoints, such as TeutonLib Peripheral Links, to a host topology. */
fun interface PeripheralEndpointProvider {
    fun endpoints(computer: ComputerContext): List<PeripheralEndpoint>
}

/** Public extension registry for hosts which compose TeutonLib's generic peripheral runtime. */
object PeripheralProviders {
    private val targets = CopyOnWriteArrayList<PeripheralTargetProvider>()
    private val endpoints = CopyOnWriteArrayList<PeripheralEndpointProvider>()

    @JvmStatic
    fun registerTargets(provider: PeripheralTargetProvider): AutoCloseable {
        targets.add(provider)
        return AutoCloseable { targets.remove(provider) }
    }

    @JvmStatic
    fun registerEndpoints(provider: PeripheralEndpointProvider): AutoCloseable {
        endpoints.add(provider)
        return AutoCloseable { endpoints.remove(provider) }
    }

    @JvmStatic
    fun owner(anchor: BlockEntity?): BlockEntity? {
        if (anchor == null) return null
        targets.forEach { provider ->
            try {
                provider.owner(anchor)?.let { return it }
            } catch (failure: RuntimeException) {
                TeutonLib.LOGGER.warn("Peripheral target provider failed", failure)
            }
        }
        return null
    }

    @JvmStatic
    fun supports(anchor: BlockEntity?): Boolean {
        if (anchor == null) return false
        targets.forEach { provider ->
            try {
                if (provider.supports(anchor)) return true
            } catch (failure: RuntimeException) {
                TeutonLib.LOGGER.warn("Peripheral target provider failed", failure)
            }
        }
        return false
    }

    @JvmStatic
    fun endpoints(computer: ComputerContext): List<PeripheralEndpoint> = buildList {
        endpoints.forEach { provider ->
            try {
                addAll(provider.endpoints(computer))
            } catch (failure: RuntimeException) {
                TeutonLib.LOGGER.warn("Peripheral endpoint provider failed", failure)
            }
        }
    }
}

/** Reserved metadata keys used by TeutonLib-provided endpoints. */
object PeripheralEndpointMetadata {
    const val LINK: String = "teutonlib:peripheral_link"
    const val ANCHOR_POSITION: String = "teutonlib:anchor_position"
    const val DEVICE_POSITION: String = "teutonlib:device_position"
    const val SIDE: String = "teutonlib:side"
}
