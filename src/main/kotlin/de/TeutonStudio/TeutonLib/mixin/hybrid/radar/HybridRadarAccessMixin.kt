package de.TeutonStudio.TeutonLib.mixin.hybrid.radar

import com.happysg.radar.block.radar.bearing.RadarContraption
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridRadarData
import net.minecraft.core.Direction
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(RadarContraption::class, remap = false)
interface HybridRadarAccessMixin : HybridRadarData {
    @Accessor("dishCount") override fun `teutonlib$getDishCount`(): Int
    @Accessor("dishCount") override fun `teutonlib$setDishCount`(value: Int)
    @Accessor("hasReceiver") override fun `teutonlib$getHasReceiver`(): Boolean
    @Accessor("hasReceiver") override fun `teutonlib$setHasReceiver`(value: Boolean)
    @Accessor("creative") override fun `teutonlib$getCreative`(): Boolean
    @Accessor("creative") override fun `teutonlib$setCreative`(value: Boolean)
    @Accessor("receiverFacing") override fun `teutonlib$getReceiverFacing`(): Direction?
    @Accessor("receiverFacing") override fun `teutonlib$setReceiverFacing`(value: Direction?)
}
