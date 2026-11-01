package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.simibubi.create.content.contraptions.Contraption
import com.simibubi.create.content.contraptions.StructureTransform
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin

@Mixin(Contraption::class)
abstract class HybridChildContraptionMixin {
    @WrapMethod(method = ["removeBlocksFromWorld(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"])
    private fun `teutonlib$assembleChild`(level: Level, offset: BlockPos, original: Operation<Void>) {
        val self = this as Any as Contraption
        HybridTrainRuntime.childWrite(self, level, HybridTrainRuntime.removalPositions(self, offset)) {
            original.call(level, offset)
        }
    }

    @WrapMethod(method = ["addBlocksToWorld(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/contraptions/StructureTransform;)V"])
    private fun `teutonlib$placeChild`(level: Level, transform: StructureTransform, original: Operation<Void>) {
        val self = this as Any as Contraption
        HybridTrainRuntime.childWrite(self, level, HybridTrainRuntime.placementPositions(self, transform)) {
            original.call(level, transform)
        }
    }
}
