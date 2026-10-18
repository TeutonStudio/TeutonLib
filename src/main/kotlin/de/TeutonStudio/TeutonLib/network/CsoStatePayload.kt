package de.TeutonStudio.TeutonLib.network

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.client.CsoClient
import java.util.function.Function
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.handling.IPayloadHandler

@JvmRecord
data class CsoStatePayload(val enabled: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<CsoStatePayload> {
        return TYPE
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<CsoStatePayload> = CustomPacketPayload.Type<CsoStatePayload>(
            ResourceLocation.fromNamespaceAndPath(
                TeutonLib.Companion.MOD_ID,
                "cso_state"
            )
        )
        @JvmField
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, CsoStatePayload> =
            StreamCodec.composite<RegistryFriendlyByteBuf, CsoStatePayload, Boolean>(
                ByteBufCodecs.BOOL,
                CsoStatePayload::enabled,
                Function { enabled: Boolean? -> CsoStatePayload(enabled!!) })

        @JvmStatic
        fun register(event: RegisterPayloadHandlersEvent) {
            // Required on both peers. The handler runs only on the logical client's main thread.
            event.registrar("1").playToClient<CsoStatePayload>(
                TYPE,
                CODEC,
                IPayloadHandler { payload: CsoStatePayload, context: IPayloadContext? -> CsoClient.apply(payload!!.enabled) })
        }
    }
}
