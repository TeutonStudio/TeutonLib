package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import dev.ryanhcode.sable.api.physics.PhysicsPipeline
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(SubLevelPhysicsSystem::class, remap = false)
abstract class HybridBuildPoseMixin {
    @Inject(method = ["updatePose(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$noDormantPose`(sub: ServerSubLevel, ci: CallbackInfo) {
        if (StationBuildRuntime.construction(sub)) { StationBuildRuntime.updatePose(sub); ci.cancel() }
    }
}

@Mixin(MergedMassTracker::class, remap = false)
abstract class HybridBuildMassUploadMixin {
    @Shadow @Final private lateinit var subLevel: ServerSubLevel

    @Inject(method = ["uploadData()V"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$deferDormantUpload`(ci: CallbackInfo) {
        if (StationBuildRuntime.construction(subLevel)) ci.cancel()
    }
}

@Mixin(ServerLevelPlot::class, remap = false)
abstract class HybridBuildLoadStatsMixin {
    @WrapOperation(method = ["load(Lnet/minecraft/nbt/CompoundTag;)V"], at = [At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;onStatsChanged(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V")], require = 1)
    private fun `teutonlib$noDormantLoadStats`(pipeline: PhysicsPipeline, sub: ServerSubLevel, original: Operation<Void>) {
        StationBuildRuntime.statsChanged(pipeline, sub) { original.call(pipeline, sub) }
    }
}
