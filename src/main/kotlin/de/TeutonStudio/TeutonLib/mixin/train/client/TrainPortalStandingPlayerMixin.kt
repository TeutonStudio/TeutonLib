package de.TeutonStudio.TeutonLib.mixin.train.client

import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalClientRiders
import net.minecraft.client.player.LocalPlayer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(LocalPlayer::class)
abstract class TrainPortalStandingPlayerMixin {
    @Inject(method = ["tick()V"], at = [At("HEAD")], require = 1)
    private fun `teutonlib$followProjectedWagon`(ci: CallbackInfo) {
        TrainPortalClientRiders.tick(this as Any as LocalPlayer)
    }
}
