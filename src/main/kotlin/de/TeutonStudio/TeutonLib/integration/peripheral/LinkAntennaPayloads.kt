package de.TeutonStudio.TeutonLib.integration.peripheral

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.client.LinkAntennaClient
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.handling.IPayloadHandler

object LinkAntennaPayloads {
    private fun handle(data: CompoundTag, player: ServerPlayer) {
        if (data.getString("action") in setOf("open", "snapshot") && !LinkAntennaRuntime.allowRequest(player)) return
        if (data.getString("action") == "open") {
            if (data.hasUUID("target")) LinkAntennaRuntime.open(player, BlockPos.of(data.getLong("position")), data.getUUID("target"))
            return
        }
        if (!data.hasUUID("session") || data.getInt("menu") != player.containerMenu.containerId) return
        val session = data.getUUID("session")
        when (val menu = player.containerMenu) {
            is LinkAntennaMenu -> {
                if (!menu.accepts(session) || !menu.stillValid(player)) return
                when (data.getString("action")) {
                    "snapshot" -> send(player, LinkAntennaRuntime.snapshot(player, menu).also { it.putUUID("session", session) })
                    "sides" -> {
                        var error = ""
                        try {
                            LinkAntennaRuntime.setSides(player, menu, data.getInt("sides"))
                        } catch (failure: IllegalArgumentException) {
                            error = failure.message ?: "teutonlib.antenna.invalid_sides"
                        } catch (failure: IllegalStateException) {
                            error = failure.message ?: "teutonlib.antenna.edit_denied"
                        }
                        send(player, LinkAntennaRuntime.snapshot(player, menu).also {
                            it.putUUID("session", session); it.putString("error", error)
                        })
                    }
                    "mark" -> if (data.hasUUID("link") && LinkLocations.get(player.server).get(data.getUUID("link"))?.target == menu.target)
                        LinkAntennaRuntime.mark(player, data.getUUID("link"), false)
                }
            }
            is PeripheralLinkMenu -> {
                if (!menu.acceptsSession(session) || !menu.stillValid(player)) return
                if (data.getString("action") == "mark_anchor") menu.link(player)?.target()?.let {
                    LinkAntennaRuntime.mark(player, it, true)
                }
            }
        }
    }
    fun send(player: ServerPlayer, data: CompoundTag) = PacketDistributor.sendToPlayer(player, State(data))
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(Command.TYPE, Command.CODEC, IPayloadHandler { payload: Command, context: IPayloadContext ->
            handle(payload.data, context.player() as ServerPlayer)
        })
        registrar.playToClient(State.TYPE, State.CODEC, IPayloadHandler { payload: State, _: IPayloadContext ->
            LinkAntennaClient.receive(payload.data)
        })
    }
    data class Command(val data: CompoundTag) : CustomPacketPayload {
        override fun type() = TYPE
        companion object {
            val TYPE = CustomPacketPayload.Type<Command>(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "antenna_command"))
            val CODEC = object : StreamCodec<RegistryFriendlyByteBuf, Command> {
                override fun decode(buffer: RegistryFriendlyByteBuf) = Command(buffer.readNbt() ?: CompoundTag())
                override fun encode(buffer: RegistryFriendlyByteBuf, value: Command) { buffer.writeNbt(value.data) }
            }
        }
    }
    data class State(val data: CompoundTag) : CustomPacketPayload {
        override fun type() = TYPE
        companion object {
            val TYPE = CustomPacketPayload.Type<State>(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "antenna_state"))
            val CODEC = object : StreamCodec<RegistryFriendlyByteBuf, State> {
                override fun decode(buffer: RegistryFriendlyByteBuf) = State(buffer.readNbt() ?: CompoundTag())
                override fun encode(buffer: RegistryFriendlyByteBuf, value: State) { buffer.writeNbt(value.data) }
            }
        }
    }
}
