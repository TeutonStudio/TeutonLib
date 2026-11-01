package de.TeutonStudio.TeutonLib

/** Server choice for replacing Create's hose with a physical entity. */
enum class HoseHeadMode {
    ALWAYS,
    SABLE_ONLY,
    NEVER;

    fun permits(onSable: Boolean): Boolean = when (this) {
        ALWAYS -> true
        SABLE_ONLY -> onSable
        NEVER -> false
    }
}
