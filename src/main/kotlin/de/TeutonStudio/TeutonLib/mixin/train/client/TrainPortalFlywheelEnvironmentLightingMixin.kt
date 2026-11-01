package de.TeutonStudio.TeutonLib.mixin.train.client

import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalFlywheelLighting
import dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment
import dev.engine_room.flywheel.backend.gl.shader.GlProgram
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Apply after Sable's injected setupDraw uniforms; indirect matrices are scoped by the pass helper. */
@Mixin(value = [EmbeddedEnvironment::class], priority = 900, remap = false)
abstract class TrainPortalFlywheelEnvironmentLightingMixin {
    @Inject(method = ["setupDraw"], at = [At("TAIL")], require = 1)
    private fun `teutonlib$useRelitFlywheelSky`(program: GlProgram, ci: CallbackInfo) {
        if (TrainPortalFlywheelLighting.relitEnvironment(this as EmbeddedEnvironment)) {
            program.setFloat("_flw_lightingSkyLightScaleUniform", 1.0f)
        }
    }
}
