package de.TeutonStudio.TeutonLib.integration

import net.neoforged.fml.loading.LoadingModList

/** The motor's private rotation cache and Create's kinetic fields are release-specific. */
object NewAgeMotorCompatibility {
    val supported = mapOf("minecraft" to "1.21.1", "create" to "6.0.10", "sable" to "2.0.6",
        "create_new_age" to "1.1.7c+mc1.21.1")

    fun supports(versions: Map<String, String>): Boolean = supported.all { (id, version) -> versions[id] == version }

    private val enabled by lazy { LoadingModList.get()?.mods?.associate { it.modId to it.version.toString() }
        ?.let(::supports) == true }

    @JvmStatic fun active(): Boolean = enabled
}
