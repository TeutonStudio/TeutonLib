package de.TeutonStudio.TeutonLib.integration.computer;

import de.TeutonStudio.TeutonLib.TeutonLib;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

public final class ComputerPayloads {
    public record Request(int menu, UUID session, String action, String name, String text, int value, boolean published) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "computer_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = new StreamCodec<>() {
            public Request decode(RegistryFriendlyByteBuf b) { return new Request(b.readVarInt(), b.readUUID(), b.readUtf(16), b.readUtf(32), b.readUtf(32), b.readVarInt(), b.readBoolean()); }
            public void encode(RegistryFriendlyByteBuf b, Request p) { b.writeVarInt(p.menu); b.writeUUID(p.session); b.writeUtf(p.action,16); b.writeUtf(p.name,32); b.writeUtf(p.text,32); b.writeVarInt(p.value); b.writeBoolean(p.published); }
        };
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Snapshot(int menu, UUID session, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "computer_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            public Snapshot decode(RegistryFriendlyByteBuf b) { return new Snapshot(b.readVarInt(), b.readUUID(), b.readNbt()); }
            public void encode(RegistryFriendlyByteBuf b, Snapshot p) { b.writeVarInt(p.menu); b.writeUUID(p.session); b.writeNbt(p.data); }
        };
        @Override public Type<Snapshot> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Request.TYPE, Request.CODEC, (p,c) -> handle(p, (ServerPlayer)c.player()));
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (p,c) -> de.TeutonStudio.TeutonLib.client.computer.ComputerPanels.accept(p));
    }
    private static void handle(Request p, ServerPlayer player) {
        if (!(player.containerMenu instanceof AbstractComputerMenu menu) || menu.containerId != p.menu || !menu.stillValid(player)) return;
        var computer = menu.getComputer(); if (computer == null || !computer.checkUsable(player)) return;
        if (!computer.isOn()) {
            CompoundTag off = new CompoundTag(); off.putString("errorKey", "offline");
            PacketDistributor.sendToPlayer(player, new Snapshot(p.menu, p.session, off)); return;
        }
        ComputerRuntime runtime = ComputerIntegration.runtime(computer); if (runtime == null) return;
        String error = "";
        try {
            switch(p.action) {
                case "snapshot" -> {}
                case "create" -> runtime.createChannel(p.name, p.text, sideName(p.value));
                case "remove" -> runtime.remove(p.name);
                case "rename" -> runtime.rename(p.name, p.text);
                case "public" -> runtime.setPublic(p.name, p.published);
                case "set" -> runtime.setValue(p.name, p.value);
                default -> { return; }
            }
        } catch (Exception e) { error = e.getMessage() == null ? "Channel operation failed" : e.getMessage(); }
        try { PacketDistributor.sendToPlayer(player, new Snapshot(p.menu, p.session, snapshot(runtime, error))); }
        catch (RuntimeException e) { TeutonLib.LOGGER.debug("Computer snapshot unavailable: {}", e.getMessage()); }
    }
    private static String sideName(int side) { return new String[]{"front", "back", "left", "right", "top", "bottom"}[Math.floorMod(side,6)]; }
    public static CompoundTag snapshot(ComputerRuntime runtime, String error) {
        CompoundTag result = new CompoundTag(); result.putString("error", error.substring(0, Math.min(160,error.length()))); result.putBoolean("dbw", DbwAccess.available());
        ListTag channels = new ListTag();
        runtime.channelViews().stream().limit(256).forEach(v -> {
            CompoundTag t = new CompoundTag(); t.putString("name", shortText(v.name())); t.putString("direction", shortText(v.direction()));
            t.putString("side", shortText(v.side())); t.putBoolean("public", v.published()); t.putInt("value", v.value());
            t.putInt("connections", v.connections()); t.putString("provider", shortText(v.provider())); t.putBoolean("editable", v.editable()); channels.add(t);
        });
        ListTag sources = new ListTag(); runtime.sourceViews().stream().limit(256).forEach(v -> {
            CompoundTag t = new CompoundTag(); t.putString("name", shortText(v.name())); t.putString("types", shortText(String.join(", ", v.types())));
            t.putString("connection", shortText(v.connection())); t.putString("position", shortText(v.devicePosition())); t.putString("link", shortText(v.linkPosition())); sources.add(t);
        });
        result.put("channels", channels); result.put("sources", sources); return result;
    }
    private static String shortText(String text) { return text == null ? "" : text.substring(0, Math.min(text.length(),256)); }
    private ComputerPayloads() {}
}
