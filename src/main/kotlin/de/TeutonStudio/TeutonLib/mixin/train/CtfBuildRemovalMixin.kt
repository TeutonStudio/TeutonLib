package de.TeutonStudio.TeutonLib.mixin.train

import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalVisuals
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.SubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Pseudo
@Mixin(targets = ["createtrainfunctionality.HeldBuilds"], remap = false)
abstract class CtfBuildRemovalMixin {
    @Inject(method = ["onSubLevelRemoved(Ldev/ryanhcode/sable/sublevel/SubLevel;Ldev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$transferIsNotDeletion`(sub: SubLevel, reason: SubLevelRemovalReason, ci: CallbackInfo) {
        if (sub is ServerSubLevel) TrainPortalVisuals.removed(sub)
        if (TrainPortalTransfers.transferring(sub.uniqueId)) ci.cancel()
    }
}
