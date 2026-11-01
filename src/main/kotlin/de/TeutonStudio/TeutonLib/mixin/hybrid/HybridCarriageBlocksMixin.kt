package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(CarriageContraptionEntity::class)
abstract class HybridCarriageBlocksMixin {
    /** Actors must not change Create's snapshot after the real plot rejected their structural write. */
    @Inject(method = ["setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate\$StructureBlockInfo;)V"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$fixedCarriageSnapshot`(local: BlockPos, block: StructureBlockInfo, ci: CallbackInfo) {
        val entity = this as Any as CarriageContraptionEntity
        val level = entity.level() as? ServerLevel ?: return
        val link = SubLevelLinks.linkHere(entity) ?: return
        val pos = link.plotOrigin.offset(local)
        if (!HybridTrainRuntime.permits(level, pos, level.getBlockState(pos), block.state())) ci.cancel()
    }
}
