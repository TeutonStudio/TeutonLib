package de.TeutonStudio.TeutonLib.traintest

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.CarriageBogey
import com.simibubi.create.content.trains.entity.CarriageContraption
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import com.simibubi.create.content.trains.entity.Train
import com.simibubi.create.content.trains.graph.TrackGraph
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.train.CtfPortalAccess
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import qouteall.imm_ptl.core.portal.Portal
import qouteall.q_misc_util.my_util.DQuaternion
import java.util.UUID

/** Synchronous transfer contract; real player seating is exercised by the ticking ride fixture. */
object TrainRiderVerification {
    fun verify(source: ServerLevel): String {
        for (same in listOf(false, true)) for (turn in listOf(0.0, 90.0)) verify(source,
            if (same) source else TrainPortalTestDestination.level(source), turn)
        return "Train riders PASS standing support replacement; rotated positions+velocity+look; " +
            "cross+same dimension; rollback support replacement; unrelated nearby rider excluded; " +
            "destination=${TrainPortalTestDestination.key.location()} portalKind=generic-ip"
    }

    private fun contraption() = CarriageContraption(Direction.NORTH).also {
        it.anchor = BlockPos.ZERO
        it.bounds = AABB(-2.0, 0.0, -2.0, 3.0, 2.0, 3.0)
        for (x in -2..2) for (z in -2..2) {
            val pos = BlockPos(x, 0, z)
            it.blocks[pos] = StructureBlockInfo(pos, Blocks.STONE.defaultBlockState(), null)
        }
        it.storage.initialize()
    }

    private fun verify(source: ServerLevel, destination: ServerLevel, turn: Double) {
        val at = Vec3(32.0, 160.0, 64.0)
        val targetAt = Vec3(96.0, 120.0, 64.0)
        for ((level, centre) in listOf(source to 2, destination to 6))
            for (x in centre - 1..centre + 1) for (z in 3..5) {
                level.setChunkForced(x, z, true)
                level.getChunk(x, z)
            }
        val carriage = Carriage(CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, CompoundTag()), null, 0)
        carriage.storage.initialize()
        Train(UUID.randomUUID(), null, TrackGraph(), listOf(carriage), emptyList(), false, 0)
        val owner = CarriageContraptionEntity.create(source, contraption()).also {
            it.setPos(at); it.setCarriage(carriage); source.addFreshEntity(it)
        }
        val original = checkNotNull(SubLevelLinks.convert(source, owner))
        val sub = SubLevelContainer.getContainer(source)!!.getSubLevel(original.subLevelId) as ServerSubLevel
        val portal = Portal(Portal.ENTITY_TYPE, source).also {
            it.setOriginPos(at); it.setDestinationDimension(destination.dimension()); it.setDestination(targetAt)
            it.setOrientationAndSize(Vec3(0.0, 0.0, 1.0), Vec3(0.0, 1.0, 0.0), 16.0, 16.0)
            it.setRotationTransformation(DQuaternion.rotationByDegrees(Vec3(0.0, 1.0, 0.0), turn))
        }
        val target = CarriageContraptionEntity.create(destination, contraption()).also {
            it.setPos(targetAt)
            // Create carries its own yaw and rotates around a block-centre pivot.
            // Match the complete source transform rather than only Entity.yRot.
            it.startAtYaw(owner.yaw - turn.toFloat())
            val mappedOrigin = portal.transformPoint(owner.toGlobalVector(Vec3.ZERO, 1f))
            it.setPos(it.position().add(mappedOrigin.subtract(it.toGlobalVector(Vec3.ZERO, 1f))))
            it.setCarriage(carriage); destination.addFreshEntity(it)
        }
        val entities = mutableSetOf<UUID>()
        fun standing(plot: Vec3): ArmorStand {
            return ArmorStand(source, 0.0, 0.0, 0.0).also {
                it.setNoGravity(true)
                it.setPos(sub.logicalPose().transformPosition(plot))
                it.setOnGround(true)
                source.addFreshEntity(it)
                (it as EntityMovementExtension).`sable$setTrackingSubLevel`(sub)
                entities += it.uuid
            }
        }
        val local = Vec3.atLowerCornerOf(original.plotOrigin).add(1.25, 1.0, 0.75)
        val rider = standing(local)
        val hull = qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge.worldAabb(sub)
        val unrelated = standing(sub.logicalPose().transformPositionInverse(
            Vec3(hull.maxX + 2.5, rider.y, (hull.minZ + hull.maxZ) * 0.5)))
        val unrelatedPosition = unrelated.position()
        check(source.getEntity(rider.uuid) === rider) { "Standing fixture entity did not enter the world" }
        check(source.getEntitiesOfClass(Entity::class.java,
            qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge.worldAabb(sub).inflate(4.0)).contains(rider)) {
            "Standing fixture chunk is not entity-accessible yet"
        }
        rider.deltaMovement = Vec3(0.1, 0.0, 0.05)
        rider.yRot = 25f
        val beforePosition = rider.position()
        val beforeVelocity = rider.deltaMovement
        val beforeLook = rider.lookAngle
        try {
            check(TrainPortalTransfers.handoff(original, sub, target, portal))
            val arrived = SubLevelContainer.getContainer(destination)!!.getSubLevel(original.subLevelId) as ServerSubLevel
            val moved = checkNotNull(destination.getEntity(rider.uuid))
            check((moved as EntityMovementExtension).`sable$getTrackingSubLevel`() === arrived) {
                "Standing support still points at the removed source build"
            }
            check(sub.isRemoved && arrived !== sub)
            check(moved.position().distanceToSqr(portal.transformPoint(beforePosition)) < 1e-6) {
                "Standing rider lost its rotated carriage-local position: ${moved.position()} vs ${portal.transformPoint(beforePosition)}"
            }
            check(moved.deltaMovement.distanceToSqr(portal.transformLocalVecNonScale(beforeVelocity)) < 1e-8)
            check(moved.lookAngle.distanceToSqr(portal.transformLocalVecNonScale(beforeLook)) < 1e-8)
            check(source.getEntity(unrelated.uuid) === unrelated && unrelated.position() == unrelatedPosition) {
                "A nearby entity with stale train support was carried without standing on this wagon: " +
                    "${unrelatedPosition} -> ${source.server.allLevels.firstNotNullOfOrNull { it.getEntity(unrelated.uuid) }?.position()}; hull=$hull"
            }
            // Reload the source again through IP's real forced-failure rollback path.
            // This must repair object identity even when no dimension changes.
            moved.setOnGround(true)
            val current = checkNotNull(SubLevelLinks.linkForSubLevel(arrived.uniqueId))
            val invalid = CarriageContraptionEntity(target.type, source)
            val back = Portal(Portal.ENTITY_TYPE, destination).also {
                it.setOriginPos(targetAt); it.setDestinationDimension(source.dimension()); it.setDestination(at)
                it.setOrientationAndSize(Vec3(0.0, 0.0, 1.0), Vec3(0.0, 1.0, 0.0), 16.0, 16.0)
            }
            check(!TrainPortalTransfers.handoff(current, arrived, invalid, back))
            val restored = SubLevelContainer.getContainer(destination)!!.getSubLevel(arrived.uniqueId) as ServerSubLevel
            val restoredRider = checkNotNull(destination.getEntity(rider.uuid))
            check((restoredRider as EntityMovementExtension).`sable$getTrackingSubLevel`() === restored)
            check(restored !== arrived && arrived.isRemoved)
        } finally {
            for (level in source.server.allLevels) {
                entities.mapNotNull(level::getEntity).forEach(Entity::discard)
                SubLevelContainer.getContainer(level)?.getSubLevel(original.subLevelId)?.let {
                    SubLevelLinks.linkForSubLevel(original.subLevelId)?.let(CtfPortalAccess::detachLink)
                    CtfPortalAccess.forgetSource(level, original.subLevelId)
                    SubLevelContainer.getContainer(level)!!.removeSubLevel(it,
                        dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED)
                }
                CtfPortalAccess.clearChunkCache(level)
            }
            owner.discard(); target.discard()
        }
    }
}
