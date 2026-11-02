package de.TeutonStudio.TeutonLib.network

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.client.train.HybridTrainClient
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import java.util.UUID

/** Small mode/config snapshot, sent only on change and on login. Plot contents
 * and machine updates continue through the existing Sable tracking protocol. */
data class HybridTrainStatePayload(val enabled: Boolean, val driving: Set<UUID>) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HybridTrainStatePayload>(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "hybrid_trains"))
        val CODEC = object : StreamCodec<RegistryFriendlyByteBuf, HybridTrainStatePayload> {
            override fun encode(buf: RegistryFriendlyByteBuf, value: HybridTrainStatePayload) {
                buf.writeBoolean(value.enabled)
                buf.writeCollection(value.driving) { buffer, id -> buffer.writeUUID(id) }
            }
            override fun decode(buf: RegistryFriendlyByteBuf) = HybridTrainStatePayload(buf.readBoolean(), buf.readList { it.readUUID() }.toSet())
        }
        fun register(event: RegisterPayloadHandlersEvent) {
            event.registrar("1").playToClient(TYPE, CODEC) { payload, _ -> HybridTrainClient.apply(payload) }
        }
    }
}
