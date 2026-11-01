package de.TeutonStudio.TeutonLib.mixin.train

import de.TeutonStudio.TeutonLib.integration.train.TrainPortalGuard
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Pseudo
@Mixin(targets = ["qouteall.imm_ptl.core.compat.sable_integration.IPShipPortalTraversalImpl"], remap = false)
abstract class FallbackTrainBuildMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["considerShip(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;J)V"],
            at = [At("HEAD")], cancellable = true, require = 1)
        private fun `teutonlib$skipTrainBuild`(server: MinecraftServer, level: ServerLevel,
            sub: ServerSubLevel, tick: Long, ci: CallbackInfo) {
            if (TrainPortalGuard.owns(level, sub)) ci.cancel()
        }
    }
}
