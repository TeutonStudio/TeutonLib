package de.TeutonStudio.TeutonLib.mixin.train.client

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalFlywheelLighting
import dev.engine_room.flywheel.api.backend.RenderContext
import dev.engine_room.flywheel.backend.engine.DrawManager
import dev.engine_room.flywheel.backend.engine.EngineImpl
import dev.engine_room.flywheel.backend.engine.LightStorage
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(value = [EngineImpl::class], remap = false)
abstract class TrainPortalFlywheelLightingMixin {
    @WrapOperation(method = ["render"], at = [At(value = "INVOKE",
        target = "Ldev/engine_room/flywheel/backend/engine/DrawManager;render(Ldev/engine_room/flywheel/backend/engine/LightStorage;Ldev/engine_room/flywheel/backend/engine/embed/EnvironmentStorage;)V")], require = 1)
    private fun `teutonlib$lightFlywheelPass`(manager: DrawManager<*>, lights: LightStorage,
        environments: EnvironmentStorage, original: Operation<Void>, context: RenderContext) {
        TrainPortalFlywheelLighting.withPass(this as EngineImpl, context) {
            original.call(manager, lights, environments)
        }
    }
}
