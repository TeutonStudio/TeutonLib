package de.TeutonStudio.TeutonLib.integration.train.client

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import createtrainfunctionality.ClientLinks
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalProjectedSupport
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.level.Level
import qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleCut
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleClientSessions
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleRender
import java.util.UUID
import java.util.WeakHashMap

/** A foreign plot cannot be a native Sable tracker: its packet coordinates belong to another world. */
object TrainPortalClientRiders {
    private data class Support(val ship: ClientSubLevel, val portal: UUID, val level: Level, val pose: Pose3d)
    private val supports = WeakHashMap<LocalPlayer, Support>()

    @JvmStatic fun tick(player: LocalPlayer) {
        val previous = supports.remove(player)
        if (!player.isAlive || player.isPassenger || !player.onGround() || player.abilities.flying) return
        val tracking = (player as EntityMovementExtension).`sable$getTrackingSubLevel`()
        if (tracking != null && !tracking.isRemoved && tracking.level === player.level()) return
        for (session in IPStraddleClientSessions.into(player.level().dimension())) {
            val ship = IPStraddleRender.shipOf(session) ?: continue
            if (ship.isRemoved || !ClientLinks.entries().any {
                    it.value == ship.plot.centerBlock && ship.level.getEntity(it.key) is CarriageContraptionEntity
                }) continue
            val current = session.isometry().mapPose(ship.logicalPose())
            val before = previous?.takeIf {
                it.ship === ship && it.portal == session.portal() && it.level === player.level()
            }?.pose ?: session.isometry().mapPose(ship.lastPose())
            val projection = IPStraddleCut.Projection(ship, session.isometry(), session.destOrigin(),
                session.destNormal(), session.portal())
            if (!TrainPortalProjectedSupport.supportsFloor(ship, before, player.position()) {
                    projection.keepsBlock(before, it)
                }) continue
            val delta = TrainPortalProjectedSupport.displacement(before, current, player.position())
            // Sable's collide() consumes this velocity until its first floor contact,
            // then applies the remaining pose change itself. Moving the player here
            // would add that native movement twice. Replacing the velocity also clears
            // residual motion when the projected wagon stops for an interpolation tick.
            (player as LivingEntityMovementExtension).`sable$getInheritedVelocity`().set(delta.x, delta.y, delta.z)
            supports[player] = Support(ship, session.portal(), player.level(), Pose3d(current))
            return
        }
    }
}
