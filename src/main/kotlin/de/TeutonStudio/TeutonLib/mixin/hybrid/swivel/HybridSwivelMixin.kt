package de.TeutonStudio.TeutonLib.mixin.hybrid.swivel

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(SwivelBearingBlockEntity::class, remap = false)
abstract class HybridSwivelMixin {
    @Unique private var `teutonlib$kineticRetry` = 0

    /** The auxiliary cog is not a separate world BE and can miss the neighbouring
     * motor's initial propagation while a plot is loading. Let Create discover its
     * real source after neighbouring block entities have initialized. */
    @Inject(method = ["tick()V"], at = [At("TAIL")])
    private fun `teutonlib$connectAuxiliaryCog`(ci: CallbackInfo) {
        if (++`teutonlib$kineticRetry` % 20 != 0) return
        val bearing = this as Any as SwivelBearingBlockEntity
        val level = bearing.level as? ServerLevel ?: return
        val extra = bearing.extraKinetics
        if (extra.hasSource() || extra.speed != 0f) return
        val parent = Sable.HELPER.getContaining(level, bearing.blockPos) as? ServerSubLevel ?: return
        if (HybridTrainRuntime.trainOwned(parent)) extra.attachKinetics()
    }

    @WrapMethod(method = ["assemble()V"])
    private fun `teutonlib$assembleChildPlot`(original: Operation<Void>) {
        val bearing = this as Any as SwivelBearingBlockEntity
        val level = bearing.level as? ServerLevel ?: run { original.call(); return }
        val parent = Sable.HELPER.getContaining(level, bearing.blockPos) as? ServerSubLevel
        if (parent == null || !HybridTrainRuntime.trainOwned(parent)) { original.call(); return }
        HybridTrainRuntime.maintain(parent) { original.call() }
        val child = bearing.subLevelID?.let { SubLevelContainer.getContainer(level)?.getSubLevel(it) as? ServerSubLevel } ?: return
        val data = child.userDataTag?.copy() ?: CompoundTag()
        data.putUUID(HybridTrainRuntime.PARENT_KEY, parent.uniqueId); child.userDataTag = data
    }

    @WrapMethod(method = ["disassemble()V"])
    private fun `teutonlib$placeChildPlot`(original: Operation<Void>) {
        val bearing = this as Any as SwivelBearingBlockEntity
        val level = bearing.level as? ServerLevel ?: run { original.call(); return }
        val parent = Sable.HELPER.getContaining(level, bearing.blockPos) as? ServerSubLevel
        if (parent == null || !HybridTrainRuntime.trainOwned(parent)) { original.call(); return }
        val child = bearing.subLevelID?.let { SubLevelContainer.getContainer(level)?.getSubLevel(it) as? ServerSubLevel }
        HybridTrainRuntime.maintain(parent) {
            if (child == null) original.call() else HybridTrainRuntime.maintain(child) { original.call() }
        }
    }
}
