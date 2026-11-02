package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import dev.ryanhcode.sable.api.physics.mass.MassData
import dev.ryanhcode.sable.api.physics.PhysicsPipeline
import dev.ryanhcode.sable.api.physics.mass.MassTracker
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.SubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/** A station owns its empty plot until assembly/cancellation, including the
 * synchronous interval when its blocks are in Create's capture path. */
@Mixin(SubLevelContainer::class, remap = false)
abstract class HybridBuildMassMixin {
    @WrapOperation(method = ["processSubLevelRemovals()V"], at = [At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/api/physics/mass/MassData;isInvalid()Z")], require = 1)
    private fun `teutonlib$keepEmptyPlot`(mass: MassData, original: Operation<Boolean>, @Local sub: ServerSubLevel): Boolean =
        !StationBuildRuntime.construction(sub) && original.call(mass)
}

@Mixin(SubLevelPhysicsSystem::class, remap = false)
abstract class HybridBuildMassChangeMixin {
    @WrapOperation(method = ["updateMassDataFromBlockChange"], at = [At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/api/physics/mass/MassTracker;isInvalid()Z")], require = 1)
    private fun `teutonlib$allowLastBlockRemoval`(mass: MassTracker, original: Operation<Boolean>,
        @Local(argsOnly = true) sub: SubLevel): Boolean =
        if (sub is ServerSubLevel && StationBuildRuntime.construction(sub)) {
            if (original.call(mass)) {
                StationBuildRuntime.park(sub)
                (mass as de.TeutonStudio.TeutonLib.integration.hybrid.ConstructionMassReset).`teutonlib$resetEmptyMass`()
            }
            false
        } else original.call(mass)

    @WrapOperation(method = ["updateMassDataFromBlockChange"], at = [At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;onStatsChanged(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V")], require = 1)
    private fun `teutonlib$noDormantStats`(pipeline: PhysicsPipeline, sub: ServerSubLevel, original: Operation<Void>) {
        StationBuildRuntime.statsChanged(pipeline, sub) { original.call(pipeline, sub) }
    }
}
