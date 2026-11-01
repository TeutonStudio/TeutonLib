package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(ServerSubLevel::class, remap = false)
abstract class HybridStructureMixin {
    /** A fixed carriage cannot split when a bearing temporarily takes blocks out of the plot. */
    @WrapOperation(method = ["tick()V"], at = [At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/sublevel/plot/heat/SubLevelHeatMapManager;tick()V")], require = 1)
    private fun `teutonlib$keepFixedCarriageTogether`(manager: SubLevelHeatMapManager, original: Operation<Void>) {
        if (!HybridTrainRuntime.locked(this as Any as ServerSubLevel)) original.call(manager)
    }
}
