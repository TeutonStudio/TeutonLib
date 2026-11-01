package de.TeutonStudio.TeutonLib.mekatest.client.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import de.TeutonStudio.TeutonLib.mekatest.client.MekanismModelProbe
import mekanism.common.network.to_client.PacketUpdateTile
import net.neoforged.neoforge.network.handling.IPayloadContext
import org.spongepowered.asm.mixin.Mixin

/** Observes the whole handler so CMC's successful cancellation is also visible. */
@Mixin(targets = ["mekanism.common.network.to_client.PacketUpdateTile"], remap = false, priority = 2000)
abstract class MekanismPacketProbeMixin {
    @WrapMethod(method = ["handle(Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V"])
    private fun teutonlibTestPacket(context: IPayloadContext, original: Operation<Void>) {
        val previous = MekanismModelProbe.beginPacket(this as Any as PacketUpdateTile, context)
        try { original.call(context) }
        finally { MekanismModelProbe.endPacket(previous) }
    }
}
