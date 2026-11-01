package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.llamalad7.mixinextras.injector.ModifyReturnValue
import com.simibubi.create.api.contraption.ContraptionType
import com.simibubi.create.content.contraptions.Contraption
import com.simibubi.create.content.trains.entity.CarriageContraption
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridCarriageContraption
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(ContraptionType::class)
abstract class HybridLoadMixin {
    private companion object {
        /** Upgrade the empty factory product before Create reads any state or mounted storage. */
        @JvmStatic
        @ModifyReturnValue(method = ["fromType(Ljava/lang/String;)Lcom/simibubi/create/content/contraptions/Contraption;"],
            at = [At("RETURN")], require = 1)
        private fun `teutonlib$loadHybrid`(original: Contraption): Contraption =
            if (ServerConfig.hybridTrains() && original.javaClass == CarriageContraption::class.java)
                HybridCarriageContraption() else original
    }
}
