package de.TeutonStudio.TeutonLib.stationtest.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import dev.ryanhcode.sable.api.physics.PhysicsPipeline
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import org.slf4j.LoggerFactory
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/** Diagnostic only: preserve the call, including its native abort, while recording the Java caller. */
@Mixin(SubLevelPhysicsSystem::class, remap = false)
abstract class StationWakeDiagnosticMixin {
    @WrapOperation(method = ["wakeUpObjectsAt(III)V"], at = [At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;wakeUp(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;)V")])
    private fun trace(pipeline: PhysicsPipeline, body: PhysicsPipelineBody, original: Operation<Void>) {
        if (java.lang.Boolean.getBoolean("teutonlib.stationAssemblyTrace") && body is ServerSubLevel && StationBuildRuntime.dormant(body)) {
            LoggerFactory.getLogger("TeutonLib/StationAssemblyRepro").error(
                "REPRO native wake requested for dormant construction {} (mass={})", body.uniqueId,
                body.selfMassTracker.mass, Throwable("Diagnostic Java call stack BEFORE native wake; not a thrown game exception"))
        }
        original.call(pipeline, body)
    }
}

@Mixin(SubLevelPhysicsSystem::class, remap = false)
abstract class StationRemovalDiagnosticMixin {
    @WrapOperation(method = ["onSubLevelRemoved"], at = [At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;remove(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V")])
    private fun traceRemoval(pipeline: PhysicsPipeline, body: ServerSubLevel, original: Operation<Void>) {
        if (java.lang.Boolean.getBoolean("teutonlib.stationAssemblyTrace") && StationBuildRuntime.dormant(body)) {
            LoggerFactory.getLogger("TeutonLib/StationAssemblyRepro").error(
                "REPRO native removal requested for dormant construction {}", body.uniqueId,
                Throwable("Diagnostic Java call stack BEFORE duplicate native removal; not a thrown game exception"))
        }
        original.call(pipeline, body)
    }
}
