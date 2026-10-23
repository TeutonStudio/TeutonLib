package de.TeutonStudio.TeutonLib.mixin.peripheral.aeronautics

import de.TeutonStudio.TeutonLib.integration.peripheral.heli.GyroscopicBearingAccess
import org.joml.Vector3d
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyVariable

/** Simulated Addition overrides the Aeronautics method hooked by [GyroscopicBearingMixin]. */
@Pseudo
@Mixin(
    targets = ["com.takkkom.simulated_addition.content.blocks.propeller.bearing.directional_gyroscopic_propeller_bearing.DirectionalGyroscopicPropellerBearingBlockEntity"],
    remap = false
)
abstract class DirectionalGyroscopicBearingMixin {
    @ModifyVariable(method = ["setTilt"], at = At("HEAD"), argsOnly = true, ordinal = 1)
    private fun `teutonlib$replaceDirectionalTarget`(automaticTarget: Vector3d): Vector3d =
        (this as Any as GyroscopicBearingAccess).`teutonlib$activeLuaTiltTarget`() ?: automaticTarget
}
