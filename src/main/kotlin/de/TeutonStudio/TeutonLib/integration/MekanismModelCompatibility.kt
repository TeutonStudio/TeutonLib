package de.TeutonStudio.TeutonLib.integration

import net.neoforged.fml.loading.LoadingModList

/** These packet and plot APIs are verified against the installed releases. */
object MekanismModelCompatibility {
    val supported = mapOf("minecraft" to "1.21.1", "mekanism" to "10.7.19",
        "sable" to "2.0.6", "ip_aeronautics" to "2.6.4", "create_mekanism_compat" to "0.1.21")

    fun supports(versions: Map<String, String>): Boolean = supported.all { (id, version) -> versions[id] == version }

    private val enabled by lazy { LoadingModList.get()?.mods?.associate { it.modId to it.version.toString() }
        ?.let(::supports) == true }

    @JvmStatic fun active(): Boolean = enabled
}
