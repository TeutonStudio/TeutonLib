package de.TeutonStudio.TeutonLib.mixin.train

import de.TeutonStudio.TeutonLib.integration.train.TrainPortalVisuals
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Watching the projected half keeps its one source body alive, as a local viewer would. */
@Mixin(ServerSubLevelContainer::class, remap = false)
abstract class TrainPortalLoadingMixin {
    @Inject(method = ["collectForceLoadedSubLevels()Ljava/util/Collection;"], at = [At("RETURN")],
        cancellable = true, require = 1)
    private fun `teutonlib$keepVisibleSource`(cir: CallbackInfoReturnable<Collection<ServerSubLevel>>) {
        val original = cir.returnValue
        val visible = TrainPortalVisuals.visibleDependencies((this as Any as ServerSubLevelContainer).level, original)
        if (visible !== original) cir.returnValue = visible
    }
}
