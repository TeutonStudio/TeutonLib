package de.TeutonStudio.TeutonLib.mixin.hybrid

import de.TeutonStudio.TeutonLib.integration.hybrid.ConstructionMassReset
import dev.ryanhcode.sable.api.physics.mass.MassTracker
import org.joml.Matrix3d
import org.joml.Vector3d
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@Mixin(MassTracker::class, remap = false)
abstract class HybridBuildMassResetMixin : ConstructionMassReset {
    @Shadow private var mass = 0.0
    @Shadow private var inverseMass = 0.0
    @Shadow private var centerOfMass: Vector3d? = null
    @Shadow private lateinit var inertiaTensor: Matrix3d
    @Shadow private lateinit var inverseInertiaTensor: Matrix3d

    override fun `teutonlib$resetEmptyMass`() {
        mass = 0.0
        inverseMass = 0.0
        centerOfMass = null
        inertiaTensor.zero()
        inverseInertiaTensor.zero()
    }
}
