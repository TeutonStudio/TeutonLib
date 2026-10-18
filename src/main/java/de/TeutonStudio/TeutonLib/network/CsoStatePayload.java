package de.TeutonStudio.TeutonLib.network;

import de.TeutonStudio.TeutonLib.TeutonLib;
import de.TeutonStudio.TeutonLib.client.CsoClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record CsoStatePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<CsoStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "cso_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CsoStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CsoStatePayload::enabled, CsoStatePayload::new);

    @Override public Type<CsoStatePayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Required on both peers. The handler runs only on the logical client's main thread.
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> CsoClient.apply(payload.enabled()));
    }
}
