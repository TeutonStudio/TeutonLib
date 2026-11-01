package de.TeutonStudio.TeutonLib.integration.train.client

/** UV2 contains two independent lightmap coordinates, including AO interpolation fractions. */
object TrainPortalLightMath {
    fun combine(local: Int, environment: Int): Int {
        val block = maxOf(local and 0xffff, environment and 0xffff).coerceAtMost(240)
        val sky = (((local ushr 16) and 0xffff).coerceAtMost(240) *
            ((environment ushr 16) and 0xffff).coerceAtMost(240) + 120) / 240
        return block or (sky shl 16)
    }
}
