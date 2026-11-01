package de.TeutonStudio.TeutonLib.mixin.train

import de.TeutonStudio.TeutonLib.integration.train.TrainPortalGuard
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.level.ServerLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData
import qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge
import de.TeutonStudio.TeutonLib.integration.train.CtfPortalAccess
import qouteall.imm_ptl.core.compat.sable_integration.IPShipRiderCarry

/** Both seamless and fallback transfers call this before any destructive transfer work. */
@Pseudo
@Mixin(targets = ["qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge"], remap = false)
abstract class ShipPortalTransferMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["reloadInDestination(ILnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)Lqouteall/imm_ptl/core/compat/sable_integration/IPSableBridge\$Loaded;"],
            at = [At("HEAD"), At("RETURN")], require = 1)
        private fun `teutonlib$invalidateReusedPlotCache`(minHeight: Int, level: ServerLevel,
            container: ServerSubLevelContainer, data: SubLevelData, cir: CallbackInfoReturnable<IPSableBridge.Loaded?>) {
            if (TrainPortalTransfers.transferring(data.uuid())) CtfPortalAccess.clearChunkCache(level)
        }

        @JvmStatic
        @WrapOperation(method = ["moveThroughPortal(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniond;Lqouteall/imm_ptl/core/compat/sable_integration/IPSableBridge\$Velocity;Ljava/util/Set;)Lqouteall/imm_ptl/core/compat/sable_integration/IPSableBridge\$Moved;"],
            at = [At(value = "INVOKE", target = "Lqouteall/imm_ptl/core/compat/sable_integration/IPSableBridge;reloadInDestination(ILnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)Lqouteall/imm_ptl/core/compat/sable_integration/IPSableBridge\$Loaded;")], require = 1)
        private fun `teutonlib$bindBeforeTransferCommit`(minHeight: Int, level: ServerLevel, container: ServerSubLevelContainer,
            data: SubLevelData, original: Operation<IPSableBridge.Loaded?>): IPSableBridge.Loaded? {
            val loaded = original.call(minHeight, level, container, data)
            TrainPortalTransfers.bindLoaded(level, loaded)
            return loaded
        }

        @JvmStatic
        @WrapOperation(method = ["moveThroughPortal(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniond;Lqouteall/imm_ptl/core/compat/sable_integration/IPSableBridge\$Velocity;Ljava/util/Set;)Lqouteall/imm_ptl/core/compat/sable_integration/IPSableBridge\$Moved;"],
            at = [At(value = "INVOKE", target = "Lqouteall/imm_ptl/core/compat/sable_integration/IPShipRiderCarry;capture(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)Lqouteall/imm_ptl/core/compat/sable_integration/IPShipRiderCarry\$Captured;")], require = 1)
        private fun `teutonlib$keepLateSeatRidersAtTheirSeat`(level: ServerLevel, sub: ServerSubLevel,
            original: Operation<IPShipRiderCarry.Captured>): IPShipRiderCarry.Captured {
            TrainPortalTransfers.beforeCaptureRiders()
            return TrainPortalTransfers.filterCapturedRiders(original.call(level, sub))
        }

        @JvmStatic
        @Inject(method = ["whyNotMovable(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerLevel;)Ljava/lang/String;"],
            at = [At("HEAD")], cancellable = true, require = 1)
        private fun `teutonlib$keepTrainBuild`(sub: ServerSubLevel, source: ServerLevel,
            destination: ServerLevel, cir: CallbackInfoReturnable<String?>) {
            if (TrainPortalGuard.owns(source, sub) && !TrainPortalTransfers.transferring(sub.uniqueId))
                cir.returnValue = TrainPortalGuard.refusal
        }
    }
}
