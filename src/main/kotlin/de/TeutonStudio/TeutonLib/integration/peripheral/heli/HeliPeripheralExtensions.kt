package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import de.TeutonStudio.TeutonLib.integration.peripheral.LinkDevice
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkExtensionMethod
import net.neoforged.fml.ModList

/** Keeps optional integration classes behind independent class-loading gates. */
internal object HeliPeripheralExtensions {
    fun methods(device: LinkDevice): Map<String, LinkExtensionMethod> {
        val additions = LinkedHashMap<String, LinkExtensionMethod>()
        if (ModList.get().isLoaded("aeronautics")) additions.putAll(BearingPeripheralExtensions.methods(device))
        return additions
    }
}
