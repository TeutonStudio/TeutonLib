package de.TeutonStudio.TeutonLib.integration

import net.neoforged.fml.loading.LoadingModList

/** The private Create pulley fields are verified against these local releases. */
object HosePulleyCompatibility {
    fun supports(versions: Map<String, String>): Boolean =
        versions["create"] == "6.0.10" && versions["sable"] == "2.0.6" &&
            "hosepulley_sable_bridge" !in versions

    private val enabled by lazy { LoadingModList.get()?.mods?.associate { it.modId to it.version.toString() }
        ?.let(::supports) == true
    }

    @JvmStatic
    fun active(): Boolean = enabled
}
