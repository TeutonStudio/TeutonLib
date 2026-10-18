package de.TeutonStudio.TeutonLib.api.computer

/** Nullable positions indicate a peripheral without a physical location. */
@JvmRecord
data class SourceView private constructor(
    val name: String, val types: List<String>, val connection: String,
    val devicePosition: String?, val linkPosition: String?, private val copied: Boolean
) {
    constructor(name: String, types: List<String>, connection: String, devicePosition: String?, linkPosition: String?) :
        this(name, java.util.List.copyOf(types), connection, devicePosition, linkPosition, true)
}
