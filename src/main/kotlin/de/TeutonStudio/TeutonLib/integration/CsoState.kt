package de.TeutonStudio.TeutonLib.integration

import kotlin.concurrent.Volatile

/** Owned by one logical side's main thread. Cleanup sees the old active state.  */
class CsoState {
    @Volatile
    private var enabled = false

    fun enabled(): Boolean {
        return enabled
    }

    fun apply(requested: Boolean, available: Boolean, removeBridges: Runnable): Boolean {
        val next = requested && available
        if (enabled == next) return false
        if (!next) removeBridges.run()
        enabled = next
        return true
    }
}
