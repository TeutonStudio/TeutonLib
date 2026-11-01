package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import createtrainfunctionality.TrainConfig
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At

@Pseudo
@Mixin(targets = ["createtrainfunctionality.AutoConvert"], remap = false)
abstract class HybridDefaultMixin {
    private companion object {
        @JvmStatic
        @ModifyExpressionValue(method = ["tick(Lnet/minecraft/server/level/ServerLevel;)V"],
            at = [At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/ModConfigSpec\$EnumValue;get()Ljava/lang/Object;")], require = 1)
        private fun `teutonlib$hybridStandard`(original: Any): Any =
            if (ServerConfig.hybridTrains()) TrainConfig.Handling.ALWAYS else original

        @JvmStatic
        @WrapMethod(method = ["consider(Lcom/simibubi/create/content/trains/entity/CarriageContraptionEntity;Lcreatetrainfunctionality/TrainConfig\$Handling;)V"])
        private fun `teutonlib$automaticConversion`(entity: CarriageContraptionEntity, handling: TrainConfig.Handling, original: Operation<Void>) {
            HybridTrainRuntime.automatically { original.call(entity, handling) }
        }
    }
}
