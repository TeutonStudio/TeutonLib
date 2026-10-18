package de.TeutonStudio.TeutonLib.integration

/** Version guard shared by early Mixin selection and runtime. */
object CsoCompatibility {
    private val supported = linkedMapOf("create" to "6.0.10", "sable" to "2.0.5", "simulated" to "1.3.2")
    @JvmStatic fun problems(versions: Map<String, String>): List<String> = supported.mapNotNull { (id, expected) ->
        val actual = versions[id]
        when {
            actual == null -> "$id fehlt (benötigt $expected)"
            actual != expected -> "$id $actual wird nicht unterstützt (benötigt $expected)"
            else -> null
        }
    }
}
