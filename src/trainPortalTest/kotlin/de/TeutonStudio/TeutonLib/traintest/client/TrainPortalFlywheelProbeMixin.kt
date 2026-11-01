package de.TeutonStudio.TeutonLib.traintest.client.mixin

import de.TeutonStudio.TeutonLib.traintest.client.TrainPortalClientVerification
import dev.engine_room.flywheel.api.backend.RenderContext
import dev.engine_room.flywheel.backend.engine.EngineImpl
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Observe arena lighting only after Flywheel's actual draw manager has rendered the pass. */
@Mixin(targets = ["de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalFlywheelLighting"], remap = false)
abstract class TrainPortalFlywheelProbeMixin {
    @Inject(method = ["withPass"], at = [At("HEAD")], remap = false)
    private fun teutonlibTestFlywheelEntry(engine: EngineImpl, context: RenderContext,
        render: () -> Unit, ci: CallbackInfo) {
        TrainPortalClientVerification.recordFlywheelPass(engine, context, "entry")
    }

    @Inject(method = ["withPass"], at = [At("RETURN")], remap = false)
    private fun teutonlibTestFlywheelDraw(engine: EngineImpl, context: RenderContext,
        render: () -> Unit, ci: CallbackInfo) {
        TrainPortalClientVerification.recordFlywheelPass(engine, context, "return")
        TrainPortalClientVerification.recordFlywheelLighting(context)
    }
}
