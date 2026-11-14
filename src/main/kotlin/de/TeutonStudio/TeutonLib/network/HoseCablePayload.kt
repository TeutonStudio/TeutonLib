package de.TeutonStudio.TeutonLib.network

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.hose.HoseCableMath
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import java.util.UUID

/** Empty points retire a strand. The UUID and dimension also cover heads outside entity tracking. */
data class HoseCablePayload(val dimension: ResourceLocation, val id: UUID, val points: List<Vec3>,
    val headPosition: Vec3, val headUp: Vec3, val docked: Boolean,
    val ownerPosition: BlockPos = BlockPos.ZERO, val dockPosition: BlockPos? = null) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HoseCablePayload>(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "hose_cable"))
        // Installed only by the client mod entry point, avoiding client class loading on servers.
        var receiver: (HoseCablePayload) -> Unit = {}
        private fun write(buf: RegistryFriendlyByteBuf, point: Vec3) {
            buf.writeDouble(point.x); buf.writeDouble(point.y); buf.writeDouble(point.z)
        }
        private fun read(buf: RegistryFriendlyByteBuf) = Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
        val CODEC = object : StreamCodec<RegistryFriendlyByteBuf, HoseCablePayload> {
            override fun encode(buf: RegistryFriendlyByteBuf, value: HoseCablePayload) {
                buf.writeResourceLocation(value.dimension); buf.writeUUID(value.id)
                buf.writeVarInt(value.points.size)
                value.points.forEach { write(buf, it) }
                write(buf, value.headPosition); write(buf, value.headUp); buf.writeBoolean(value.docked)
                buf.writeBlockPos(value.ownerPosition); buf.writeBoolean(value.dockPosition != null)
                value.dockPosition?.let(buf::writeBlockPos)
            }
            override fun decode(buf: RegistryFriendlyByteBuf): HoseCablePayload {
                val dimension = buf.readResourceLocation(); val id = buf.readUUID(); val size = buf.readVarInt()
                require(size == 0 || size in 2..HoseCableMath.MAX_POINTS) { "Invalid hose point count: $size" }
                val points = List(size) { read(buf) }
                val position = read(buf); val up = read(buf); val docked = buf.readBoolean()
                val owner = buf.readBlockPos(); val dock = if (buf.readBoolean()) buf.readBlockPos() else null
                require((size == 0 || HoseCableMath.valid(points)) && listOf(position, up).all {
                    it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
                }) { "Non-finite hose pose" }
                return HoseCablePayload(dimension, id, points, position, up, docked, owner, dock)
            }
        }
        fun register(event: RegisterPayloadHandlersEvent) {
            event.registrar("1").playToClient(TYPE, CODEC) { payload, _ -> receiver(payload) }
        }
    }
}
