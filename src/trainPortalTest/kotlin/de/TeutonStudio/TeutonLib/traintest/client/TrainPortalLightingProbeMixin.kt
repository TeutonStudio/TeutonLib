package de.TeutonStudio.TeutonLib.traintest.client.mixin

import de.TeutonStudio.TeutonLib.traintest.client.TrainPortalClientVerification
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Sable uses the same block-format VBO path when Sodium supplies world rendering. */
@Mixin(VanillaChunkedSubLevelRenderData::class, remap = false)
abstract class TrainPortalLightingProbeMixin {
    @Shadow @Final private lateinit var subLevel: ClientSubLevel

    @Inject(method = ["renderChunkedSubLevel"], at = [At("TAIL")], remap = false)
    private fun teutonlibTestUploadedLighting(ci: CallbackInfo) {
        TrainPortalClientVerification.recordAppliedLighting(subLevel)
    }
}
