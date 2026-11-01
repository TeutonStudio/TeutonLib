package de.TeutonStudio.TeutonLib.mixin.train

import de.TeutonStudio.TeutonLib.integration.train.TrainPortalVisuals
import net.minecraft.server.level.ServerPlayer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyVariable

/** A newly opened client world needs the existing crossing announced once again. */
@Pseudo
@Mixin(targets = ["qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleSync"], remap = false)
abstract class TrainPortalViewerSyncMixin {
    private companion object {
        @JvmStatic
        @ModifyVariable(method = ["tick(Lnet/minecraft/server/MinecraftServer;)V"],
            at = At("STORE"), ordinal = 0, require = 1)
        private fun `teutonlib$restoreViewerSession`(player: ServerPlayer): ServerPlayer {
            TrainPortalVisuals.syncViewer(player)
            return player
        }
    }
}
