package de.TeutonStudio.TeutonLib.mixin.train

import de.TeutonStudio.TeutonLib.integration.train.TrainPortalGuard
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalVisuals
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.level.ServerLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Do not start a ship crossing session or apply ship push-back to a rail-driven body. */
@Pseudo
@Mixin(targets = ["qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleController"], remap = false)
abstract class StraddlingTrainBuildMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["reset()V"], at = [At("HEAD")], require = 1)
        private fun `teutonlib$clearTrainViews`(ci: CallbackInfo) { TrainPortalVisuals.reset() }

        @JvmStatic
        @Inject(method = ["considerShip(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;JLjava/util/Set;Ljava/util/List;)V"],
            at = [At("HEAD")], cancellable = true, require = 1)
        private fun `teutonlib$skipTrainBuild`(level: ServerLevel, sub: ServerSubLevel, tick: Long,
            seen: Set<*>, handoffs: List<*>, ci: CallbackInfo) {
            if (TrainPortalGuard.owns(level, sub)) {
                TrainPortalVisuals.refresh(level, sub, tick, seen)
                ci.cancel()
            }
        }
    }
}
