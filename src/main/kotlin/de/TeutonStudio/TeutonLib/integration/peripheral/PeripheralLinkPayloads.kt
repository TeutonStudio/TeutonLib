package de.TeutonStudio.TeutonLib.integration.peripheral

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.SourceApi
import de.TeutonStudio.TeutonLib.client.PeripheralLinkScreen
import de.TeutonStudio.TeutonLib.integration.computer.ComputerIntegration
import de.TeutonStudio.TeutonLib.integration.computer.SourceIntrospection
import de.TeutonStudio.TeutonLib.integration.computer.SourceNicknameHost
import java.util.LinkedHashSet
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.handling.IPayloadHandler

/** Server-authoritative snapshots and nickname commands for the Peripheral Link screen. */
object PeripheralLinkPayloads {
    @JvmStatic
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(
            Request.TYPE,
            Request.CODEC,
            IPayloadHandler { payload: Request, context: IPayloadContext ->
                handle(payload, context.player() as ServerPlayer)
            }
        )
        registrar.playToClient(
            Response.TYPE,
            Response.CODEC,
            IPayloadHandler { payload: Response, _: IPayloadContext -> PeripheralLinkScreen.accept(payload) }
        )
    }

    private fun handle(request: Request, player: ServerPlayer) {
        val menu = player.containerMenu as? PeripheralLinkMenu ?: return
        if (menu.containerId != request.menu || !menu.acceptsSession(request.session) || !menu.stillValid(player)) return
        val link = menu.link(player) ?: return
        var error = ""
        try {
            when (request.action) {
                "snapshot" -> Unit
                "nickname" -> {
                    require(!player.isSpectator) { "Spectators cannot edit Peripheral Links" }
                    require(player.mayUseItemAt(menu.position, Direction.UP, ItemStack.EMPTY)) {
                        "You cannot edit this Peripheral Link"
                    }
                    setNickname(link, request.text)
                }
                "clear_nickname" -> {
                    require(!player.isSpectator) { "Spectators cannot edit Peripheral Links" }
                    require(player.mayUseItemAt(menu.position, Direction.UP, ItemStack.EMPTY)) {
                        "You cannot edit this Peripheral Link"
                    }
                    setNickname(link, "")
                }
                else -> error("Unsupported Peripheral Link action")
            }
        } catch (failure: IllegalArgumentException) {
            error = failure.message ?: "Peripheral Link action failed"
        } catch (failure: IllegalStateException) {
            error = failure.message ?: "Peripheral Link action failed"
        }
        val snapshot = snapshot(link)
        snapshot.putString("error", error.take(256))
        PacketDistributor.sendToPlayer(player, Response(request.menu, request.session, snapshot))
    }

    private fun setNickname(link: PeripheralLinkBlockEntity, nickname: String) {
        link.nickname(nickname)
        val connection = link.connection ?: return
        val host = connection.owner
        (host as? SourceNicknameHost)?.`teutonlib$sourceNicknames`()?.set(connection.name(), "")
        host.setChanged()
    }

    @JvmStatic
    fun snapshot(link: PeripheralLinkBlockEntity): CompoundTag {
        LinkRegistry.validate()
        val level = link.level as? ServerLevel
        val connection = link.connection
        return CompoundTag().also { tag ->
            tag.putString("nickname", link.nickname())
            tag.putString("status", link.status)
            tag.putString("address", "cc_aeroworks:link_${link.identity()}")
            tag.putString("linkPosition", link.blockPos.toShortString())
            tag.putString("devicePosition", link.devicePosition().toShortString())
            tag.putString(
                "side",
                link.blockState.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING).opposite.name
            )
            level?.let { tag.putString("linkDimension", it.dimension().location().toString()) }
            link.target()?.let { tag.putString("targetId", it.toString()) }

            val anchor = connection?.anchor ?: level?.let { LinkRegistry.anchor(it, link.target()) }
            anchor?.let {
                link.rememberTarget(it)
                tag.putString("targetPosition", it.blockPos.toShortString())
                tag.putString("targetBlock", BuiltInRegistries.BLOCK.getKey(it.blockState.block).toString())
                it.level?.let { targetLevel ->
                    tag.putString("targetDimension", targetLevel.dimension().location().toString())
                }
            } ?: run {
                link.knownTargetPosition()?.let { tag.putString("targetPosition", it.toShortString()) }
                if (link.knownTargetBlock().isNotEmpty()) tag.putString("targetBlock", link.knownTargetBlock())
                if (link.knownTargetDimension().isNotEmpty()) tag.putString("targetDimension", link.knownTargetDimension())
            }
            connection?.owner?.let {
                tag.putString("computerPosition", it.blockPos.toShortString())
                tag.putString("computerBlock", BuiltInRegistries.BLOCK.getKey(it.blockState.block).toString())
            }
            if (level != null) loadedBlockId(level, link.devicePosition())?.let { tag.putString("deviceBlock", it) }

            if (connection != null) {
                tag.putString("primaryType", connection.peripheral.type)
                tag.put("additionalTypes", ListTag().also { list ->
                    connection.peripheral.additionalTypes.forEach { list.add(StringTag.valueOf(it)) }
                })
                val types = LinkedHashSet<String>().apply {
                    add(connection.peripheral.type)
                    addAll(connection.peripheral.additionalTypes)
                }
                tag.put("types", ListTag().also { list -> types.forEach { list.add(StringTag.valueOf(it)) } })
                sourceApi(connection)?.let { tag.put("api", it.encode()) }
            }
        }
    }

    private fun loadedBlockId(level: ServerLevel, position: BlockPos): String? {
        val chunk = level.chunkSource.getChunkNow(position.x shr 4, position.z shr 4) ?: return null
        return BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(position).block).toString()
    }

    private fun sourceApi(connection: LinkRegistry.Connection): SourceApi {
        val runtime = ComputerIntegration.active().firstOrNull { candidate ->
            runCatching { candidate.host() === connection.owner }.getOrDefault(false)
        }
        if (runtime != null) {
            val view = runtime.sourceViews().firstOrNull { it.name == connection.name() }
            if (view != null) {
                val exposed = runtime.peripherals()[connection.name()] ?: connection.peripheral
                return SourceIntrospection.describe(runtime, view, exposed)
            }
        }
        val peripheral = connection.peripheral
        val types = LinkedHashSet<String>().apply {
            add(peripheral.type)
            addAll(peripheral.additionalTypes)
        }.toList()
        return SourceApi(
            connection.name(),
            types,
            "wireless",
            SourceIntrospection.documentedMethods(peripheral, emptyList()),
            SourceIntrospection.modemDetails(
                peripheral,
                connection.link.level!!.dimension().location().toString()
            ),
            connection.link.devicePosition().toShortString(),
            connection.link.blockPos.toShortString()
        )
    }

    data class Request(val menu: Int, val session: UUID, val action: String, val text: String) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<Request> = TYPE

        companion object {
            @JvmField
            val TYPE = CustomPacketPayload.Type<Request>(
                ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "peripheral_link_request")
            )
            @JvmField
            val CODEC = object : StreamCodec<RegistryFriendlyByteBuf, Request> {
                override fun decode(buffer: RegistryFriendlyByteBuf) = Request(
                    buffer.readVarInt(), buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(32)
                )

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: Request) {
                    buffer.writeVarInt(payload.menu)
                    buffer.writeUUID(payload.session)
                    buffer.writeUtf(payload.action, 32)
                    buffer.writeUtf(payload.text, 32)
                }
            }
        }
    }

    data class Response(val menu: Int, val session: UUID, val data: CompoundTag) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<Response> = TYPE

        companion object {
            @JvmField
            val TYPE = CustomPacketPayload.Type<Response>(
                ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "peripheral_link_snapshot")
            )
            @JvmField
            val CODEC = object : StreamCodec<RegistryFriendlyByteBuf, Response> {
                override fun decode(buffer: RegistryFriendlyByteBuf) =
                    Response(buffer.readVarInt(), buffer.readUUID(), buffer.readNbt() ?: CompoundTag())

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: Response) {
                    buffer.writeVarInt(payload.menu)
                    buffer.writeUUID(payload.session)
                    buffer.writeNbt(payload.data)
                }
            }
        }
    }
}
