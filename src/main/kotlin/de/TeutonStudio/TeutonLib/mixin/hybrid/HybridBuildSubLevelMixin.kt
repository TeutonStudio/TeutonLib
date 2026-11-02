package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import de.TeutonStudio.TeutonLib.ServerConfig
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import net.neoforged.neoforge.common.ModConfigSpec

@Mixin(ServerSubLevel::class)
abstract class HybridBuildSubLevelMixin {
    @Inject(method = ["tick()V"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$holdConstruction`(ci: CallbackInfo) {
        val sub = this as Any as ServerSubLevel
        if (sub.isRemoved) { ci.cancel(); return }
        StationBuildRuntime.hold(sub)
    }

    @Inject(method = ["onPlotBoundsChanged()V"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$keepEmptyConstruction`(ci: CallbackInfo) {
        val sub = this as Any as ServerSubLevel
        if (StationBuildRuntime.construction(sub)) {
            StationBuildRuntime.preserveBounds(sub)
            ci.cancel()
        }
    }

    @WrapOperation(method = ["tick()V"], at = [At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/ModConfigSpec\$BooleanValue;getAsBoolean()Z")], require = 1)
    private fun `teutonlib$keepWagonTogether`(setting: ModConfigSpec.BooleanValue, original: Operation<Boolean>): Boolean {
        val sub = this as Any as ServerSubLevel
        return !sub.isRemoved && !StationBuildRuntime.construction(sub) &&
            !(ServerConfig.hybridTrains() && HybridTrainRuntime.owningRoot(sub) === sub) && original.call(setting)
    }
}
