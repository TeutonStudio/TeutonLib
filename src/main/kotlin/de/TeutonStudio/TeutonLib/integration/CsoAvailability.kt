package de.TeutonStudio.TeutonLib.integration

import net.neoforged.fml.loading.LoadingModList

/** Safe during early Mixin selection; never links optional mod classes. */
object CsoAvailability {
    @JvmStatic fun problems(): List<String> {
        val mods = LoadingModList.get() ?: return listOf("Modliste ist noch nicht verfügbar")
        return CsoCompatibility.problems(mods.mods.associate { it.modId to it.version.toString() })
    }
    @JvmStatic fun available(): Boolean = problems().isEmpty()
}
