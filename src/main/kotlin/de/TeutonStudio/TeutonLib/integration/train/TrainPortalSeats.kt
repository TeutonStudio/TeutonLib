package de.TeutonStudio.TeutonLib.integration.train

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.Vec3
import qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge
import qouteall.imm_ptl.core.compat.sable_integration.IPShipCreateCompat
import qouteall.imm_ptl.core.compat.sable_integration.IPShipRiderCarry
import qouteall.imm_ptl.core.compat.sable_straddle.IPPortalIsometry
import qouteall.imm_ptl.core.compat.sable_straddle.IPSeatProxies
import qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleSeats
import qouteall.imm_ptl.core.portal.Portal
import qouteall.q_misc_util.api.McRemoteProcedureCall
import qouteall.q_misc_util.my_util.DQuaternion
import java.util.UUID

/** Keep a seat's source-side rider on IP's native proxy until that seat actually crosses. */
class TrainPortalSeats private constructor(
    private val source: ServerLevel,
    private val ship: UUID,
    private val isometry: IPPortalIsometry,
    private val origin: Vec3,
    private val normal: Vec3,
    private val riders: List<Rider>
) {
    private data class Rider(val player: ServerPlayer, val seat: BlockPos, val plot: Vec3, val position: Vec3)
    private val ids = riders.mapTo(hashSetOf()) { it.player.uuid }
    private var detached = false

    fun detach() {
        if (detached) return
        riders.forEach { it.player.removeVehicle() }
        detached = true
    }

    fun filter(captured: IPShipRiderCarry.Captured): IPShipRiderCarry.Captured {
        if (ids.isEmpty()) return captured
        return IPShipRiderCarry.Captured(captured.bindings().filterNot { it.uuid() in ids },
            captured.mounts().filterNot { it.passenger() in ids },
            captured.seats().filterNot { it.riderUuid() in ids }, captured.subPosBefore())
    }

    fun rollback() {
        if (!detached) return
        detached = false
        for (rider in riders) if (rider.player.isAlive && rider.player.level() === source) {
            rider.player.setPos(rider.position)
            if (!IPShipCreateCompat.sitDown(source, rider.seat, rider.player))
                IPShipRiderCarry.queueSit(source.dimension(), rider.seat, rider.player.uuid)
        }
    }

    fun arrived(moved: IPSableBridge.Moved) {
        detached = false
        val destination = moved.sub().level
        val inverse = isometry.inverse()
        val shift = moved.shift()
        for (rider in riders) {
            val player = rider.player
            if (!player.isAlive || player.level() !== source || player.isPassenger) continue
            val display = EntityType.BLOCK_DISPLAY.create(source) as? Display.BlockDisplay ?: continue
            val plot = rider.plot.add(shift.x.toDouble(), shift.y.toDouble(), shift.z.toDouble())
            val position = inverse.mapPoint(moved.sub().logicalPose().transformPosition(plot))
            display.moveTo(position.x, position.y, position.z, 0f, 0f)
            display.addTag("ip_aeronautics_seat_proxy")
            markProxy.invoke(null, display.uuid, false)
            check(source.addFreshEntity(display)) { "Could not create the source-side train seat view" }
            val proxy = constructor.newInstance(player.uuid, ship, destination.dimension(), rider.seat.offset(shift),
                plot, inverse, origin, normal, display)
            players()[player.uuid] = proxy
            entities()[display.uuid] = proxy
            check(player.startRiding(display, true)) { "Could not attach the source-side train seat rider" }
            val rotation = inverse.rotation()
            McRemoteProcedureCall.tellClientToInvoke(player,
                "qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleSeatsClient.RemoteCallables.begin",
                display.uuid, ship, destination.dimension(), plot, inverse.origin(), inverse.dest(),
                DQuaternion(rotation.x(), rotation.y(), rotation.z(), rotation.w()))
        }
    }

    companion object {
        // The outer and inner native classes are fingerprinted before this access is enabled.
        private val type = IPStraddleSeats::class.java
        private val proxyType = Class.forName(type.name + "\$Proxy")
        private val constructor = proxyType.getDeclaredConstructor(UUID::class.java, UUID::class.java,
            ResourceKey::class.java, BlockPos::class.java, Vec3::class.java, IPPortalIsometry::class.java,
            Vec3::class.java, Vec3::class.java, Display.BlockDisplay::class.java).also { it.isAccessible = true }
        private val playersField = type.getDeclaredField("BY_PLAYER").also { it.isAccessible = true }
        private val entitiesField = type.getDeclaredField("BY_ENTITY").also { it.isAccessible = true }
        private val markProxy = IPSeatProxies::class.java.getDeclaredMethod("add", UUID::class.java,
            Boolean::class.javaPrimitiveType).also { it.isAccessible = true }

        @Suppress("UNCHECKED_CAST") private fun players() = playersField.get(null) as MutableMap<UUID, Any>
        @Suppress("UNCHECKED_CAST") private fun entities() = entitiesField.get(null) as MutableMap<UUID, Any>

        fun capture(sub: ServerSubLevel, portal: Portal): TrainPortalSeats {
            val source = sub.level
            val isometry = IPPortalIsometry.of(portal)
            val keep = TrainPortalTransfers.crossingFor(sub)?.takeIf { it.portal() === portal }
                ?.keepNormal() ?: portal.normal
            val riders = source.players().mapNotNull { player ->
                val seat = player.vehicle as? SeatEntity ?: return@mapNotNull null
                if (players().containsKey(player.uuid) || Sable.HELPER.getContaining(seat) !== sub ||
                    player.eyePosition.subtract(portal.originPos).dot(keep) <= 0.1) return@mapNotNull null
                Rider(player, seat.blockPosition(), sub.logicalPose().transformPositionInverse(player.position()),
                    player.position())
            }
            return TrainPortalSeats(source, sub.uniqueId, isometry, portal.destPos, isometry.mapVec(keep), riders)
        }
    }
}
