package de.TeutonStudio.TeutonLib.integration.hybrid

import net.minecraft.core.Direction

/** Implemented only on the fingerprinted RadarContraption, without loading optional radar classes in common hooks. */
interface HybridRadarData {
    fun `teutonlib$getDishCount`(): Int
    fun `teutonlib$setDishCount`(value: Int)
    fun `teutonlib$getHasReceiver`(): Boolean
    fun `teutonlib$setHasReceiver`(value: Boolean)
    fun `teutonlib$getCreative`(): Boolean
    fun `teutonlib$setCreative`(value: Boolean)
    fun `teutonlib$getReceiverFacing`(): Direction?
    fun `teutonlib$setReceiverFacing`(value: Direction?)
}
