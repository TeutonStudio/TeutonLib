package de.TeutonStudio.TeutonLib.integration.computer

import kotlin.math.*
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.ChannelView
import de.TeutonStudio.TeutonLib.api.computer.SourceView
import de.TeutonStudio.TeutonLib.client.computer.ComputerPanels
import java.lang.Exception
import java.lang.RuntimeException
import java.util.UUID
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.handling.IPayloadHandler

object ComputerPayloads {
    @JvmStatic
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer<Request>(
            Request.Companion.TYPE,
            Request.Companion.CODEC,
            IPayloadHandler { p: Request, c: IPayloadContext? ->
                ComputerPayloads.handle(
                    p!!,
                    c!!.player() as ServerPlayer
                )
            })
        registrar.playToClient<Snapshot>(
            Snapshot.Companion.TYPE,
            Snapshot.Companion.CODEC,
            IPayloadHandler { p: Snapshot, c: IPayloadContext? -> ComputerPanels.Companion.accept(p) })
    }

    @JvmStatic
    private fun handle(p: Request, player: ServerPlayer) {
        val menu = player.containerMenu as? AbstractComputerMenu ?: return
        if (menu.containerId != p.menu || !menu.stillValid(player)) return
        if (player.isSpectator && p.action != "snapshot") return
        val computer: ServerComputer = menu.computer
        if (computer == null || !computer.checkUsable(player)) return
        if (!computer.isOn()) {
            val off = CompoundTag()
            off.putString("errorKey", "offline")
            PacketDistributor.sendToPlayer(player, Snapshot(p.menu, p.session, off))
            return
        }
        val runtime = ComputerIntegration.runtime(computer)
        if (runtime == null) return
        var error = ""
        try {
            when (p.action) {
                "snapshot" -> {}
                "create" -> runtime.createChannel(p.name, p.text, sideName(p.value))
                "remove" -> runtime.remove(p.name)
                "rename" -> runtime.rename(p.name, p.text)
                "public" -> runtime.setPublic(p.name, p.published)
                "set" -> runtime.setValue(p.name, p.value)
                else -> {
                    return
                }
            }
        } catch (e: Exception) {
            error = (if (e.message == null) "Channel operation failed" else e.message)!!
        }
        try {
            PacketDistributor.sendToPlayer(player, Snapshot(p.menu, p.session, snapshot(runtime, error)))
        } catch (e: RuntimeException) {
            TeutonLib.Companion.LOGGER.debug("Computer snapshot unavailable: {}", e.message)
        }
    }

    @JvmStatic
    private fun sideName(side: Int): String? {
        return arrayOf<String>("front", "back", "left", "right", "top", "bottom")[Math.floorMod(side, 6)]
    }

    @JvmStatic
    fun snapshot(runtime: ComputerRuntime, error: String): CompoundTag {
        val result = CompoundTag()
        result.putString("error", error.substring(0, min(160, error.length)))
        result.putBoolean("dbw", DbwAccess.available())
        val channels = ListTag()
        runtime.channelViews().stream().limit(256).forEach { v: ChannelView? ->
            val t = CompoundTag()
            t.putString("name", shortText(v!!.name))
            t.putString("direction", shortText(v.direction))
            t.putString("side", shortText(v.side))
            t.putBoolean("public", v.published)
            t.putInt("value", v.value)
            t.putInt("connections", v.connections)
            t.putString("provider", shortText(v.provider))
            t.putBoolean("editable", v.editable)
            channels.add(t)
        }
        val sources = ListTag()
        runtime.sourceViews().stream().limit(256).forEach { v: SourceView? ->
            val t = CompoundTag()
            t.putString("name", shortText(v!!.name))
            t.putString("types", shortText(v.types.joinToString(", ")))
            t.putString("connection", shortText(v.connection))
            t.putString("position", shortText(v.devicePosition))
            t.putString("link", shortText(v.linkPosition))
            sources.add(t)
        }
        result.put("channels", channels)
        result.put("sources", sources)
        return result
    }

    @JvmStatic
    private fun shortText(text: kotlin.String?): kotlin.String {
        return if (text == null) "" else text.substring(0, min(text.length, 256))
    }

    @JvmRecord
    data class Request(
        val menu: Int,
        val session: UUID,
        val action: kotlin.String,
        val name: kotlin.String,
        val text: kotlin.String,
        val value: Int,
        val published: Boolean
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<Request> {
            return TYPE
        }

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<Request> = CustomPacketPayload.Type<Request>(
                ResourceLocation.fromNamespaceAndPath(
                    TeutonLib.Companion.MOD_ID,
                    "computer_request"
                )
            )
            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, Request> =
                object : StreamCodec<RegistryFriendlyByteBuf, Request> {
                    override fun decode(b: RegistryFriendlyByteBuf): Request {
                        return Request(
                            b.readVarInt(),
                            b.readUUID(),
                            b.readUtf(16),
                            b.readUtf(32),
                            b.readUtf(32),
                            b.readVarInt(),
                            b.readBoolean()
                        )
                    }

                    override fun encode(b: RegistryFriendlyByteBuf, p: Request) {
                        b.writeVarInt(p.menu)
                        b.writeUUID(p.session)
                        b.writeUtf(p.action, 16)
                        b.writeUtf(p.name, 32)
                        b.writeUtf(p.text, 32)
                        b.writeVarInt(p.value)
                        b.writeBoolean(p.published)
                    }
                }
        }
    }

    @JvmRecord
    data class Snapshot(val menu: Int, val session: UUID, val data: CompoundTag?) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<Snapshot> {
            return TYPE
        }

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<Snapshot> = CustomPacketPayload.Type<Snapshot>(
                ResourceLocation.fromNamespaceAndPath(
                    TeutonLib.Companion.MOD_ID,
                    "computer_snapshot"
                )
            )
            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, Snapshot> =
                object : StreamCodec<RegistryFriendlyByteBuf, Snapshot> {
                    override fun decode(b: RegistryFriendlyByteBuf): Snapshot {
                        return Snapshot(b.readVarInt(), b.readUUID(), b.readNbt())
                    }

                    override fun encode(b: RegistryFriendlyByteBuf, p: Snapshot) {
                        b.writeVarInt(p.menu)
                        b.writeUUID(p.session)
                        b.writeNbt(p.data)
                    }
                }
        }
    }
}
