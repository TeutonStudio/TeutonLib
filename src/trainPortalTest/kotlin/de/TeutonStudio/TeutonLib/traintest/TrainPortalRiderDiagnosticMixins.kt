package de.TeutonStudio.TeutonLib.traintest.client.mixin

import de.TeutonStudio.TeutonLib.integration.train.TrainPortalRiders
import de.TeutonStudio.TeutonLib.traintest.TrainRiderDiagnostics
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import qouteall.imm_ptl.core.portal.Portal

@Mixin(value = [TrainPortalRiders.Companion::class], remap = false)
abstract class TrainPortalRiderCaptureDiagnosticMixin {
    @Inject(method = ["capture(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lqouteall/imm_ptl/core/portal/Portal;)Lde/TeutonStudio/TeutonLib/integration/train/TrainPortalRiders;"],
        at = [At("RETURN")], require = 1)
    private fun `teutonlib$observeCapturedRiders`(sub: ServerSubLevel, portal: Portal,
        cir: CallbackInfoReturnable<TrainPortalRiders>) {
        TrainRiderDiagnostics.capture(cir.returnValue, sub, portal)
    }
}

@Mixin(value = [TrainPortalRiders::class], remap = false)
abstract class TrainPortalRiderRestoreDiagnosticMixin {
    @Inject(method = ["restore(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Z)V"], at = [At("HEAD")], require = 1)
    private fun `teutonlib$observeBeforeRiderRestore`(sub: ServerSubLevel, rollback: Boolean, ci: CallbackInfo) {
        TrainRiderDiagnostics.restore("restore-before", this as Any as TrainPortalRiders, sub, rollback)
    }

    @Inject(method = ["restore(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Z)V"], at = [At("RETURN")], require = 1)
    private fun `teutonlib$observeAfterRiderRestore`(sub: ServerSubLevel, rollback: Boolean, ci: CallbackInfo) {
        TrainRiderDiagnostics.restore("restore-after", this as Any as TrainPortalRiders, sub, rollback)
    }
}
