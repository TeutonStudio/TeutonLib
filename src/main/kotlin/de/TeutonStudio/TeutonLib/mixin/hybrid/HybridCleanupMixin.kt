package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo

@Pseudo
@Mixin(targets = ["createtrainfunctionality.SubLevelLinks"], remap = false)
abstract class HybridCleanupMixin {
    private companion object {
        @JvmStatic
        @WrapMethod(method = ["releaseBlocks(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/world/phys/Vec3;)V"])
        private fun `teutonlib$releaseOwnedPlot`(level: ServerLevel, sub: ServerSubLevel, drop: Vec3?, original: Operation<Void>) {
            HybridTrainRuntime.maintain(sub) { original.call(level, sub, drop) }
        }
    }
}
