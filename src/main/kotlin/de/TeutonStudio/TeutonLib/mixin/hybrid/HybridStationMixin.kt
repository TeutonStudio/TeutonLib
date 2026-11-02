package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.simibubi.create.content.trains.entity.CarriageContraption
import com.simibubi.create.content.trains.station.StationBlockEntity
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridCarriageContraption
import net.minecraft.core.Direction
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Redirect
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import com.simibubi.create.content.contraptions.AssemblyException
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.UUID

@Mixin(StationBlockEntity::class)
abstract class HybridStationMixin {
    @Shadow protected var lastException: AssemblyException? = null
    @Shadow protected var failedCarriageIndex: Int = -1

    @Inject(method = ["remove()V"], at = [At("HEAD")], require = 1)
    private fun `teutonlib$removeStation`(ci: CallbackInfo) {
        StationBuildRuntime.removed(this as Any as StationBlockEntity)
    }

    @Inject(method = ["enterAssemblyMode(Lnet/minecraft/server/level/ServerPlayer;)Z"], at = [At("RETURN")], require = 1)
    private fun `teutonlib$startBuild`(player: net.minecraft.server.level.ServerPlayer?, cir: CallbackInfoReturnable<Boolean>) {
        if (cir.returnValue) StationBuildRuntime.begin(this as Any as StationBlockEntity)
    }

    @Redirect(method = ["refreshAssemblyInfo()V", "trackClicked"], at = At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"), require = 1)
    private fun `teutonlib$buildState`(level: Level, pos: BlockPos): BlockState =
        StationBuildRuntime.state(this as Any as StationBlockEntity, level, pos)

    @Redirect(method = ["trackClicked"], at = At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"), require = 1)
    private fun `teutonlib$buildEntity`(level: Level, pos: BlockPos): BlockEntity? =
        StationBuildRuntime.blockEntity(this as Any as StationBlockEntity, level, pos)

    @Redirect(method = ["trackClicked"], at = At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), require = 1)
    private fun `teutonlib$buildBogey`(level: Level, pos: BlockPos, state: BlockState, flags: Int): Boolean =
        StationBuildRuntime.place(this as Any as StationBlockEntity, level, pos, state, flags)

    @Redirect(method = ["trackClicked"], at = At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"), require = 1)
    private fun `teutonlib$replaceBogey`(level: Level, pos: BlockPos, drops: Boolean): Boolean =
        StationBuildRuntime.destroy(this as Any as StationBlockEntity, level, pos, drops)

    @WrapMethod(method = ["assemble(Ljava/util/UUID;)V"])
    private fun `teutonlib$finishBuild`(player: UUID, original: Operation<Void>) {
        val station = this as Any as StationBlockEntity
        try { StationBuildRuntime.assemble(station) { original.call(player) } }
        catch (failure: IllegalStateException) {
            lastException = AssemblyException(Component.literal(failure.message ?: "Train construction failed"))
            failedCarriageIndex = -1
            station.notifyUpdate()
        }
    }

    @WrapMethod(method = ["exitAssemblyMode()Z"])
    private fun `teutonlib$leaveBuild`(original: Operation<Boolean>): Boolean {
        val station = this as Any as StationBlockEntity
        try { StationBuildRuntime.finish(station) }
        catch (failure: IllegalStateException) {
            lastException = AssemblyException(Component.literal(failure.message ?: "Train construction area is obstructed"))
            station.notifyUpdate()
            return false
        }
        return original.call()
    }

    @Redirect(method = ["assemble(Ljava/util/UUID;)V"], at = At(value = "NEW",
        target = "(Lnet/minecraft/core/Direction;)Lcom/simibubi/create/content/trains/entity/CarriageContraption;"), require = 1)
    private fun `teutonlib$assembleHybrid`(direction: Direction): CarriageContraption =
        if (ServerConfig.hybridTrains() || StationBuildRuntime.constructing()) HybridCarriageContraption(direction) else CarriageContraption(direction)
}
