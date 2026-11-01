package de.TeutonStudio.TeutonLib.integration.hose

/** Pure tensile spring: no compression, and overload time is measured in physics seconds. */
object HoseTension {
    fun force(distance: Double, length: Double, separatingSpeed: Double, stiffness: Double, damping: Double): Double {
        if (distance <= length || distance <= 1e-9) return 0.0
        return (stiffness * (distance - length) + damping * separatingSpeed).coerceAtLeast(0.0)
    }

    fun impulse(force: Double, seconds: Double, inverseMass: Double, stiffness: Double, damping: Double): Double =
        force * seconds / (1.0 + seconds * (damping + stiffness * seconds) * inverseMass.coerceAtLeast(0.0))

    fun overload(previous: Double, force: Double, maximum: Double, seconds: Double): Double =
        if (force > maximum) previous + seconds else 0.0
}

/** Half-rate shared allowance. Fractional mB survive ticks, but unused capacity never accumulates. */
class HoseFlowBudget {
    private var tick = Long.MIN_VALUE
    private var fraction = 0.0
    private var remaining = 0

    fun available(time: Long, nativeRate: Int): Int {
        if (tick != time) {
            tick = time
            val exact = nativeRate.coerceAtLeast(0) * 0.5 + fraction
            remaining = exact.toInt()
            fraction = exact - remaining
        }
        return remaining
    }

    fun consume(amount: Int) { remaining = (remaining - amount).coerceAtLeast(0) }
}
