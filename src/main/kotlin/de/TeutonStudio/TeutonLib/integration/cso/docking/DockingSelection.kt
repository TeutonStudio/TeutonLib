package de.TeutonStudio.TeutonLib.integration.cso.docking

/** Stable candidate ranking, independent of Minecraft and of previous effective lengths. */
object DockingSelection {
    data class Candidate<T>(val value: T, val distanceSquared: Double, val positionOrder: Long)

    fun <T> choose(candidates: Sequence<Candidate<T>>): T? = candidates
        .filter { it.distanceSquared.isFinite() && it.distanceSquared >= 0 }
        .minWithOrNull(compareBy<Candidate<T>> { it.distanceSquared }.thenBy { it.positionOrder })?.value
}
