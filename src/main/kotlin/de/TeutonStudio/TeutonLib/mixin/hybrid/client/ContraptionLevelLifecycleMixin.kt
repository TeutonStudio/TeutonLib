package de.TeutonStudio.TeutonLib.mixin.hybrid.client

import de.TeutonStudio.TeutonLib.client.train.HybridTrainClient
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientSubLevel::class)
abstract class ContraptionLevelLifecycleMixin {
    @Inject(method = ["tick()V"], at = [At("TAIL")], require = 1)
    private fun `teutonlib$selectGraphicalLevel`(ci: CallbackInfo) {
        HybridTrainClient.update(this as Any as ClientSubLevel)
    }
}
