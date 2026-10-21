package de.TeutonStudio.TeutonLib.integration.cso.docking

/** Pure policy: no Minecraft or optional mod classes are linked here. */
enum class DockingLength(val blocks: Float) {
    OFF(0f), HALF(0.5f), FULL(1f);

    val contactOffset: Double get() = 0.5 + blocks
    val baseDistance: Int get() = if (this == OFF) 0 else (2 * contactOffset).toInt()

    fun negotiate(other: DockingLength): DockingLength = entries[minOf(ordinal, other.ordinal)]
    fun acceptsGridDistance(distance: Int): Boolean = this != OFF && distance == baseDistance

    companion object {
        fun fromSignal(signal: Int): DockingLength = when (signal.coerceIn(0, 15)) {
            in 0..5 -> OFF
            in 6..10 -> HALF
            else -> FULL
        }
        fun fromSaved(value: Int): DockingLength = entries.getOrNull(value) ?: OFF
    }
}
