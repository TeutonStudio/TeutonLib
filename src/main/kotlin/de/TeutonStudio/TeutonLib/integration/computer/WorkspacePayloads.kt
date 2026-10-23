package de.TeutonStudio.TeutonLib.integration.computer

import kotlin.math.*
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace
import de.TeutonStudio.TeutonLib.client.computer.WorkspacePanels
import java.lang.AutoCloseable
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.util.Objects
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

/** One transport for addon workspaces; a response belongs to one menu, controller and page.  */
object WorkspacePayloads {
    private val SOURCE_NICKNAME_ACTIONS = setOf("source_nickname", "clear_source_nickname")
    private val BACKENDS: MutableMap<ResourceLocation, ComputerWorkspace> =
        ConcurrentHashMap<ResourceLocation, ComputerWorkspace>()

    @JvmStatic
    fun contains(id: ResourceLocation?): Boolean {
        return BACKENDS.containsKey(id)
    }

    @JvmStatic
    fun register(id: ResourceLocation, backend: ComputerWorkspace): AutoCloseable {
        require(BACKENDS.putIfAbsent(id, backend) == null) { "Duplicate workspace: " + id }
        return AutoCloseable { BACKENDS.remove(id, backend) }
    }

    @JvmStatic
    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer<Request>(
            Request.Companion.TYPE,
            Request.Companion.CODEC,
            IPayloadHandler { p: Request, c: IPayloadContext? ->
                WorkspacePayloads.handle(
                    p!!,
                    c!!.player() as ServerPlayer
                )
            })
        registrar.playToClient<Response>(
            Response.Companion.TYPE,
            Response.Companion.CODEC,
            IPayloadHandler { p: Response, c: IPayloadContext? -> WorkspacePanels.Companion.accept(p) })
    }

    @JvmStatic
    private fun handle(p: Request, player: ServerPlayer) {
        val menu = player.containerMenu as? AbstractComputerMenu ?: return
        if (menu.containerId != p.menu || !menu.stillValid(player)) return
        if (p.command.page !in 1..2 || (player.isSpectator && p.command.action != "snapshot")) return
        val computer: ServerComputer = menu.computer
        val backend = BACKENDS.get(p.backend)
        if (computer == null || backend == null || !computer.checkUsable(player) || !backend.owns(computer)) return
        var error = ""
        try {
            check(computer.isOn()) { "Computer is off" }
            if (p.command.action != "snapshot") {
                if (p.command.action in SOURCE_NICKNAME_ACTIONS) {
                    require(p.command.page == 1) { "Invalid source action" }
                    val current = backend.snapshot(player, computer, 1)
                    fun find(entries: List<ComputerWorkspace.Entry>): ComputerWorkspace.Entry? {
                        for (entry in entries) {
                            if (entry.id == p.command.selected) return entry
                            find(entry.children)?.let { return it }
                        }
                        return null
                    }
                    val selected = find(current.entries) ?: error("Peripheral is no longer available")
                    require(selected.actions.any { it.id == p.command.action && !it.clientSide }) {
                        "Action is no longer available"
                    }
                    StandardComputerWorkspace.execute(player, computer, p.command)
                } else backend.execute(player, computer, p.command)
            }
        } catch (e: IllegalArgumentException) {
            error = Objects.toString(e.message, "Workspace action failed")
        } catch (e: IllegalStateException) {
            error = Objects.toString(e.message, "Workspace action failed")
        }
        try {
            val snapshot = if (computer.isOn())
                backend.snapshot(player, computer, p.command.page)
            else
                ComputerWorkspace.Snapshot(
                    "",
                    "Computer is off",
                    mutableListOf<ComputerWorkspace.Entry>(),
                    mutableListOf<ComputerWorkspace.Action>()
                )
            val data = (if (computer.isOn() && p.command.page == 1) RedstoneSources.append(snapshot, computer) else snapshot).encode()
            data.putString("error", error.substring(0, min(256, error.length)))
            PacketDistributor.sendToPlayer(player, Response(p.menu, p.session, p.backend, p.command.page, data))
        } catch (e: RuntimeException) {
            TeutonLib.Companion.LOGGER.debug("Workspace unavailable", e)
        }
    }

    @JvmRecord
    data class Request(
        val menu: Int,
        val session: UUID,
        val backend: ResourceLocation,
        val command: ComputerWorkspace.Command
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<Request> {
            return TYPE
        }

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<Request> = CustomPacketPayload.Type<Request>(
                ResourceLocation.fromNamespaceAndPath(
                    TeutonLib.Companion.MOD_ID,
                    "workspace_request"
                )
            )
            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, Request> =
                object : StreamCodec<RegistryFriendlyByteBuf, Request> {
                    override fun decode(b: RegistryFriendlyByteBuf): Request {
                        return Request(
                            b.readVarInt(), b.readUUID(), b.readResourceLocation(), ComputerWorkspace.Command(
                                b.readVarInt(),
                                b.readUtf(32),
                                b.readUtf(512),
                                b.readUtf(128),
                                b.readUtf(512),
                                b.readUtf(32)
                            )
                        )
                    }

                    override fun encode(b: RegistryFriendlyByteBuf, p: Request) {
                        b.writeVarInt(p.menu)
                        b.writeUUID(p.session)
                        b.writeResourceLocation(p.backend)
                        val c = p.command
                        b.writeVarInt(c.page)
                        b.writeUtf(c.action, 32)
                        b.writeUtf(c.selected, 512)
                        b.writeUtf(c.group, 128)
                        b.writeUtf(c.target, 512)
                        b.writeUtf(c.text, 32)
                    }
                }
        }
    }

    @JvmRecord
    data class Response(
        val menu: Int,
        val session: UUID,
        val backend: ResourceLocation,
        val page: Int,
        val data: CompoundTag?
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<Response> {
            return TYPE
        }

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<Response> = CustomPacketPayload.Type<Response>(
                ResourceLocation.fromNamespaceAndPath(
                    TeutonLib.Companion.MOD_ID,
                    "workspace_snapshot"
                )
            )
            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, Response> =
                object : StreamCodec<RegistryFriendlyByteBuf, Response> {
                    override fun decode(b: RegistryFriendlyByteBuf): Response {
                        return Response(
                            b.readVarInt(),
                            b.readUUID(),
                            b.readResourceLocation(),
                            b.readVarInt(),
                            b.readNbt()
                        )
                    }

                    override fun encode(b: RegistryFriendlyByteBuf, p: Response) {
                        b.writeVarInt(p.menu)
                        b.writeUUID(p.session)
                        b.writeResourceLocation(p.backend)
                        b.writeVarInt(p.page)
                        b.writeNbt(p.data)
                    }
                }
        }
    }
}
