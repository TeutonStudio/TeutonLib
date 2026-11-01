package de.TeutonStudio.TeutonLib.traintest.client.mixin

import de.TeutonStudio.TeutonLib.traintest.client.TrainPortalClientVerification

import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.minecraft.client.renderer.ShaderInstance
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Development-only observer. It never invokes or changes a render operation. */
@Mixin(targets = ["qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleRender"], remap = false)
abstract class TrainPortalDrawProbeMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["beginShipDraw"], at = [At("RETURN")], remap = false)
        private fun teutonlibTestDraw(ship: ClientSubLevel, shader: ShaderInstance,
            camX: Double, camY: Double, camZ: Double, cir: CallbackInfoReturnable<Boolean>) {
            TrainPortalClientVerification.recordDraw(ship, cir.returnValue)
        }
    }
}
