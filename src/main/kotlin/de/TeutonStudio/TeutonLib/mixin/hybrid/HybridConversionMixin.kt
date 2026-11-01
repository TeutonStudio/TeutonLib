package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import net.minecraft.server.level.ServerLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Pseudo
@Mixin(targets = ["createtrainfunctionality.SubLevelLinks"], remap = false)
abstract class HybridConversionMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["convert(Lnet/minecraft/server/level/ServerLevel;Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;)Lcreatetrainfunctionality/SubLevelLinks\$Link;"],
            at = [At("RETURN")], require = 1)
        private fun `teutonlib$conversionMode`(level: ServerLevel, entity: AbstractContraptionEntity, cir: CallbackInfoReturnable<SubLevelLinks.Link?>) {
            HybridTrainRuntime.converted(level, entity, cir.returnValue)
        }
    }
}
