package de.TeutonStudio.TeutonLib.integration.train

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3d
import qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge
import qouteall.imm_ptl.core.compat.sable_integration.IPShipRiderCarry
import qouteall.imm_ptl.core.compat.sable_straddle.IPPortalIsometry
import qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleCut
import qouteall.imm_ptl.core.portal.Portal
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

/** IP carries entities, but its replay does not replace Sable's removed standing support. */
class TrainPortalRiders private constructor(
    private val source: net.minecraft.server.level.ServerLevel,
    private val pose: Pose3d,
    private val origin: BlockPos,
    private val riders: List<Rider>
) {
    private data class Rider(val id: UUID, val plotPosition: Vec3, val velocity: Vec3, val look: Vec3,
        val sourceSide: Boolean, val latePlayer: Boolean)
    private val latePlayers = riders.filter { it.latePlayer }.mapTo(hashSetOf()) { it.id }

    fun filter(captured: IPShipRiderCarry.Captured): IPShipRiderCarry.Captured {
        // IP's general ship hull can overlap another wagon or a nearby platform.
        // Exact contacts captured above own all standing player movement here;
        // retain IP's native path only for mounts verified to belong to this plot.
        val mountedHere = captured.seats().mapTo(hashSetOf()) { it.riderUuid() }
        val excluded = captured.bindings().mapNotNullTo(hashSetOf()) { binding ->
            val player = source.server.playerList.getPlayer(binding.uuid())
            binding.uuid().takeIf { it in latePlayers || player != null &&
                (!player.isPassenger || it !in mountedHere) }
        }
        if (excluded.isEmpty()) return captured
        return IPShipRiderCarry.Captured(captured.bindings().filterNot { it.uuid() in excluded },
            captured.mounts().filterNot { it.passenger() in excluded },
            captured.seats().filterNot { it.riderUuid() in excluded }, captured.subPosBefore())
    }

    fun restore(sub: ServerSubLevel, rollback: Boolean = false) {
        val shift = sub.plot.centerBlock.subtract(origin)
        val turn = Quaterniond(sub.logicalPose().orientation()).mul(Quaterniond(pose.orientation()).conjugate())
        val server = sub.level.server
        for (rider in riders) {
            if (rollback && !rider.sourceSide) continue // its remote body/position never moved
            val entity = server.playerList.getPlayer(rider.id)
                ?: server.allLevels.firstNotNullOfOrNull { it.getEntity(rider.id) } ?: continue
            if (!entity.isAlive || entity.isPassenger) continue
            if (!rollback && rider.latePlayer && entity.level() === source) {
                // The body centre crossed before this part of the deck. Leave the
                // player on its source-side image until the player's own crossing.
                // A foreign canonical tracker would use the other world's pose.
                entity.setPos(pose.transformPosition(rider.plotPosition))
                (entity as EntityStickExtension).`sable$setPlotPosition`(null)
                (entity as EntityMovementExtension).`sable$setTrackingSubLevel`(null)
                entity.`sable$setLastTrackingSubLevelID`(null)
                entity.setPortalCooldown(0)
                continue
            }
            val plot = rider.plotPosition.add(shift.x.toDouble(), shift.y.toDouble(), shift.z.toDouble())
            val world = sub.logicalPose().transformPosition(plot)
            val look = rotate(turn, rider.look)
            val turnPlayer = entity is ServerPlayer && entity.lookAngle.distanceToSqr(look) > 1e-8
            val yaw = Math.toDegrees(atan2(-look.x, look.z)).toFloat()
            val pitch = Math.toDegrees(atan2(-look.y, sqrt(look.x * look.x + look.z * look.z))).toFloat()
            // IP's player teleport packet reads the player's current angles.
            entity.yRot = yaw
            entity.xRot = pitch
            val carried = if (entity.level() !== sub.level || entity.position().distanceToSqr(world) > 1e-8 || turnPlayer) {
                if (entity is ServerPlayer) {
                    ServerTeleportationManager.of(server).forceTeleportPlayer(entity, sub.level.dimension(), world)
                    entity
                } else ServerTeleportationManager.teleportRegularEntityTo(entity, sub.level.dimension(), world)
                    ?: continue
            } else entity
            // A plot reload keeps its UUID but replaces the SubLevel object and may relocate
            // its plot. Keeping either old reference makes the next movement tick drop it.
            (carried as EntityStickExtension).`sable$setPlotPosition`(null)
            (carried as EntityMovementExtension).`sable$setTrackingSubLevel`(sub)
            carried.deltaMovement = rotate(turn, rider.velocity)
            carried.yRot = yaw
            carried.xRot = pitch
            carried.fallDistance = 0f
            carried.setOnGround(true)
            carried.setPortalCooldown(300)
            carried.hurtMarked = true
            EntitySubLevelUtil.setOldPosNoMovement(carried)
        }
    }

    companion object {
        fun capture(sub: ServerSubLevel, portal: Portal): TrainPortalRiders {
            val source = sub.level
            val pose = Pose3d(sub.logicalPose())
            val keepNormal = TrainPortalTransfers.crossingFor(sub)?.takeIf { it.portal() === portal }
                ?.keepNormal() ?: portal.normal
            val riders = linkedMapOf<UUID, Rider>()
            fun remember(entity: Entity, sourcePosition: Vec3, velocity: Vec3, look: Vec3, sourceSide: Boolean) {
                if (!entity.isAlive || entity.isPassenger || entity is Portal || entity is AbstractContraptionEntity) return
                val latePlayer = sourceSide && entity is ServerPlayer &&
                    entity.eyePosition.subtract(portal.originPos).dot(keepNormal) > 0.1
                riders[entity.uuid] = Rider(entity.uuid, pose.transformPositionInverse(sourcePosition), velocity, look,
                    sourceSide, latePlayer)
            }
            // Association, rather than hull proximity, distinguishes adjacent wagons.
            for (entity in source.getEntitiesOfClass(Entity::class.java, IPSableBridge.worldAabb(sub).inflate(4.0))) {
                if (entity.onGround() && (entity as EntityMovementExtension).`sable$getTrackingSubLevel`() === sub &&
                    TrainPortalProjectedSupport.supportsFloor(sub, pose, entity.position()))
                    remember(entity, entity.position(), entity.deltaMovement, entity.lookAngle, true)
            }
            // A player may cross before the wagon centre. IP intentionally gives its
            // projected collision body no foreign-world tracking reference. Accept only
            // a floor contact on this exact projection, never all entities in its AABB.
            val destination = source.server.getLevel(portal.destDim)
            if (destination != null) {
                val isometry = IPPortalIsometry.of(portal)
                val projection = IPStraddleCut.projectionOf(destination, sub)
                    ?: IPStraddleCut.Projection(sub, isometry, isometry.mapPoint(portal.originPos),
                        isometry.mapVec(keepNormal.scale(-1.0)), portal.uuid)
                val mappedPose = isometry.mapPose(pose)
                for (entity in destination.getEntitiesOfClass(Entity::class.java,
                        projection.mappedBounds().inflate(1.0))) {
                    if (riders.containsKey(entity.uuid) || entity.isPassenger || !entity.onGround()) continue
                    val tracking = (entity as EntityMovementExtension).`sable$getTrackingSubLevel`()
                    if (tracking != null && tracking !== sub) continue
                    // Resolve this wagon's exact block/shape. IP's generic near/floor
                    // helpers can choose different overlapping wagon projections.
                    if (!TrainPortalProjectedSupport.supportsFloor(sub, mappedPose, entity.position()) {
                            projection.keepsBlock(mappedPose, it)
                        }) continue
                    val inverse = projection.isometry().inverse()
                    remember(entity, inverse.mapPoint(entity.position()), inverse.mapVec(entity.deltaMovement),
                        inverse.mapVec(entity.lookAngle), false)
                }
            }
            return TrainPortalRiders(source, pose, sub.plot.centerBlock, riders.values.toList())
        }

        private fun rotate(rotation: Quaterniond, vector: Vec3): Vec3 {
            val transformed = rotation.transform(Vector3d(vector.x, vector.y, vector.z))
            return Vec3(transformed.x, transformed.y, transformed.z)
        }
    }
}
