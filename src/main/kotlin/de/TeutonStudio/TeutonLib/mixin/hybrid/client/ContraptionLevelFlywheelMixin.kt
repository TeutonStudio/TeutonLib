package de.TeutonStudio.TeutonLib.mixin.hybrid.client

import de.TeutonStudio.TeutonLib.client.train.HybridTrainClient
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.FlywheelCompatNeoForge
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(FlywheelCompatNeoForge::class, remap = false)
abstract class ContraptionLevelFlywheelMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["preVisualizationFrame(Lnet/minecraft/world/level/Level;F)V"], at = [At("TAIL")], require = 1)
        private fun `teutonlib$refreshTrainEmbeddings`(level: Level, partialTick: Float, ci: CallbackInfo) {
            // A station can immediately reuse a construction plot. Sable removes
            // its old UUID's cached embedding here, after the replacement's
            // entity-join event has already run. Recreate from the current body.
            HybridTrainClient.refreshEmbeddings(level)
        }
    }
}
