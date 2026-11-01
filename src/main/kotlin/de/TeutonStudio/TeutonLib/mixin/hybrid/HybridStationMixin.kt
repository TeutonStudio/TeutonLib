package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.simibubi.create.content.trains.entity.CarriageContraption
import com.simibubi.create.content.trains.station.StationBlockEntity
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridCarriageContraption
import net.minecraft.core.Direction
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Redirect

@Mixin(StationBlockEntity::class)
abstract class HybridStationMixin {
    @Redirect(method = ["assemble(Ljava/util/UUID;)V"], at = At(value = "NEW",
        target = "(Lnet/minecraft/core/Direction;)Lcom/simibubi/create/content/trains/entity/CarriageContraption;"), require = 1)
    private fun `teutonlib$assembleHybrid`(direction: Direction): CarriageContraption =
        if (ServerConfig.hybridTrains()) HybridCarriageContraption(direction) else CarriageContraption(direction)
}
