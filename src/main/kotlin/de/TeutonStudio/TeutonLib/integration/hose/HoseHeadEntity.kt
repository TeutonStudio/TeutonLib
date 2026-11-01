package de.TeutonStudio.TeutonLib.integration.hose

import de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry
import de.TeutonStudio.TeutonLib.integration.sable.SableHoseOwnership
import de.TeutonStudio.TeutonLib.HoseHeadMode
import de.TeutonStudio.TeutonLib.ServerConfig
import java.util.UUID
import kotlin.math.max
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * Persistent colliding hose end. Its owner stays in Sable storage coordinates while all motion
 * and the spherical length constraint use world coordinates, including rotated sublevels.
 */
class HoseHeadEntity(type: EntityType<out HoseHeadEntity>, level: Level) : Entity(type, level) {
    val dockPosition: BlockPos? get() = entityData.get(DOCK_POSITION).orElse(null)
    val dockId: UUID? get() = entityData.get(DOCK_ID).orElse(null)

    fun isCarriedBy(player: Player): Boolean = carrier == player.uuid
    fun validOwner(): com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity? {
        val owner = HosePipeGraph.loaded(level(), ownerPosition) as? com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity ?: return null
        return owner.takeIf { isAlive && !it.isRemoved && (it as? HosePulleyHost)?.physicalHeadId == uuid && HoseHeadPolicy.enabled(it) }
    }
    fun connector(): HoseDrainConnection? {
        val id = dockId ?: return null
        val connector = HoseConnectorRegistry.find(level(), id, uuid) ?: return null
        if (dockPosition != connector.blockPos && !level().isClientSide) {
            entityData.set(DOCK_POSITION, java.util.Optional.of(connector.blockPos.immutable()))
        }
        return connector
    }
    fun attach(connector: HoseDrainConnection) {
        entityData.set(DOCK_POSITION, java.util.Optional.of(connector.blockPos.immutable()))
        entityData.set(DOCK_ID, java.util.Optional.of(connector.identity))
        stopCarrying()
        ejectPassengers()
        deltaMovement = Vec3.ZERO
        setPos(connector.socketWorld())
    }
    fun detach() {
        entityData.set(DOCK_POSITION, java.util.Optional.empty())
        entityData.set(DOCK_ID, java.util.Optional.empty())
        deltaMovement = Vec3.ZERO
    }
    fun carry(player: Player, hand: InteractionHand = InteractionHand.MAIN_HAND) {
        carrierHand = hand
        carrierSlot = player.inventory.selected
        carrier = player.uuid
        ejectPassengers()
        deltaMovement = Vec3.ZERO
    }

    private var configured = false
    // The client must also skip the carried head when picking the block behind it.
    private var carrier: UUID?
        get() = entityData.get(CARRIER).orElse(null)
        set(value) { entityData.set(CARRIER, java.util.Optional.ofNullable(value)) }
    private var carrierHand = InteractionHand.MAIN_HAND
    private var carrierSlot = -1

    private fun stillHoldingWrench(player: Player): Boolean =
        BuiltInRegistries.ITEM.getKey(player.getItemInHand(carrierHand).item) == WRENCH &&
            (carrierHand != InteractionHand.MAIN_HAND || player.inventory.selected == carrierSlot)

    private fun stopCarrying() {
        carrier = null
        carrierSlot = -1
    }

    fun throwCarried(player: Player): Boolean {
        if (!isCarriedBy(player) || !stillHoldingWrench(player)) return false
        stopCarrying()
        deltaMovement = player.lookAngle.scale(1.35).add(0.0, 0.25, 0.0)
        hasImpulse = true
        return true
    }
    private var missingOwnerTicks = 0
    private var missingDockTicks = 0
    private var lastWorldAnchor: Vec3? = null

    init {
        blocksBuilding = true
    }

    val ownerPosition: BlockPos get() = entityData.get(OWNER)
    val hoseLength: Float get() = entityData.get(LENGTH)

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(CARRIER, java.util.Optional.empty<UUID>())
        builder.define(OWNER, BlockPos.ZERO)
        builder.define(LENGTH, 0f)
        builder.define(DOCK_POSITION, java.util.Optional.empty<BlockPos>())
        builder.define(DOCK_ID, java.util.Optional.empty<UUID>())
    }

    fun configure(owner: BlockPos, length: Float) {
        entityData.set(OWNER, owner.immutable())
        setHoseLength(length)
        configured = true
    }

    fun belongsTo(position: BlockPos): Boolean = configured && ownerPosition == position

    fun setHoseLength(length: Float) {
        entityData.set(LENGTH, if (length.isFinite()) max(0f, length) else 0f)
    }

    fun worldAnchor(): Vec3 {
        if (level().hasChunkAt(ownerPosition) && !SableHoseOwnership.ownerRemoved(level(), ownerPosition)) {
            val projected = SubLevelGeometry.projectToWorld(
                level(), Vec3(ownerPosition.x + 0.5, ownerPosition.y.toDouble(), ownerPosition.z + 0.5)
            )
            lastWorldAnchor = projected
            return projected
        }
        // Never interpret an unloaded sublevel's storage position as a world-space anchor.
        return lastWorldAnchor ?: centerPosition().add(0.0, hoseLength + END_ALLOWANCE, 0.0)
    }

    /** Retraction may stop here when a collision prevents the end from moving towards its owner. */
    fun minimumHoseLength(): Float = max(0.0, centerPosition().distanceTo(worldAnchor()) - END_ALLOWANCE).toFloat()

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        if (!configured) {
            discard()
            return
        }
        if (ServerConfig.hoseHeadMode() == HoseHeadMode.NEVER) {
            discard()
            return
        }
        if (ownerWasRemoved()) {
            // Assembly relocates block NBT before the new pulley receives its first tick.
            // Entity ticks may run first; give that pulley time to rebind this UUID.
            if (++missingOwnerTicks >= 3) discard()
            stopCarrying()
            deltaMovement = Vec3.ZERO
            return
        }
        missingOwnerTicks = 0
        if (!HoseHeadPolicy.allowed(level(), ownerPosition)) {
            discard()
            return
        }
        if (!level().hasChunkAt(ownerPosition)) {
            stopCarrying()
            deltaMovement = Vec3.ZERO
            return
        }

        if (dockId != null) {
            val connector = connector()
            if (connector != null) {
                missingDockTicks = 0
                setPos(connector.socketWorld())
                deltaMovement = Vec3.ZERO
                return
            }
            val pos = dockPosition
            // An unloaded socket is neither a free hose nor proof of a removed connector.
            if (pos != null && !level().hasChunkAt(pos) && !SableHoseOwnership.ownerRemoved(level(), pos)) {
                deltaMovement = Vec3.ZERO
                return
            }
            // Assembly moves NBT before the destination block entity initializes.
            if (++missingDockTicks < 40) { deltaMovement = Vec3.ZERO; return }
            detach()
        }
        val anchor = worldAnchor()
        val heldBy = carrier?.let(level()::getPlayerByUUID)
        if (heldBy != null && heldBy.isAlive && !heldBy.isSpectator && stillHoldingWrench(heldBy)) {
            val target = heldBy.eyePosition.add(heldBy.lookAngle.scale(1.15))
            moveCenterTo(constrain(target, anchor))
            deltaMovement = Vec3.ZERO
            // A player walking away from a taut hose drops it instead of remotely holding it.
            if (centerPosition().distanceToSqr(heldBy.eyePosition) > 16.0) stopCarrying()
            return
        }
        stopCarrying()

        deltaMovement = deltaMovement.add(0.0, -0.08, 0.0)
        move(MoverType.SELF, deltaMovement)
        deltaMovement = deltaMovement.multiply(0.91, if (onGround()) 0.65 else 0.98, 0.91)

        val radial = centerPosition().subtract(anchor)
        val allowed = hoseLength + END_ALLOWANCE
        if (radial.lengthSqr() > allowed * allowed) {
            val outward = radial.normalize()
            // move() preserves Minecraft/Sable collision handling even while the pulley retracts.
            moveCenterTo(anchor.add(outward.scale(allowed)))
            val radialSpeed = deltaMovement.dot(outward)
            if (radialSpeed > 0.0) deltaMovement = deltaMovement.subtract(outward.scale(radialSpeed))
        }
    }

    private fun ownerWasRemoved(): Boolean {
        // Sable and ordinary chunks can unload before the independently tracked physical head.
        // Only a loaded owner position proves that the pulley has actually been removed.
        if (SableHoseOwnership.ownerRemoved(level(), ownerPosition)) return true
        if (!level().hasChunkAt(ownerPosition)) return false
        if (BuiltInRegistries.BLOCK.getKey(level().getBlockState(ownerPosition).block) != HOSE_PULLEY) return true
        val owner = level().getBlockEntity(ownerPosition) as? HosePulleyHost
        return owner?.physicalHeadId != uuid
    }

    private fun centerPosition(): Vec3 = position().add(0.0, bbHeight * 0.5, 0.0)

    private fun constrain(target: Vec3, anchor: Vec3): Vec3 {
        val radial = target.subtract(anchor)
        val allowed = hoseLength + END_ALLOWANCE
        return if (radial.lengthSqr() <= allowed * allowed) target
        else anchor.add(radial.normalize().scale(allowed))
    }

    private fun moveCenterTo(target: Vec3) = move(MoverType.SELF, target.subtract(centerPosition()))

    override fun interact(player: Player, hand: InteractionHand): InteractionResult {
        if (BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).item) != WRENCH) return InteractionResult.PASS
        if (level().isClientSide) return InteractionResult.SUCCESS
        if (!player.isAlive || player.isSpectator) return InteractionResult.PASS
        if (dockId != null) {
            connector()?.release() ?: detach()
            carry(player, hand)
            return InteractionResult.CONSUME
        }
        if (carrier != null && carrier != player.uuid) return InteractionResult.CONSUME

        when {
            carrier == player.uuid -> {
                throwCarried(player)
            }
            hoseLength <= 0.05f && !player.isShiftKeyDown -> player.startRiding(this)
            else -> {
                carry(player, hand)
            }
        }
        level().playSound(null, blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.45f, 1.4f)
        return InteractionResult.CONSUME
    }

    override fun remove(reason: RemovalReason) {
        if (!level().isClientSide && reason.shouldDestroy()) connector()?.release()
        super.remove(reason)
    }

    override fun positionRider(passenger: Entity, move: MoveFunction) {
        move.accept(passenger, x, y - passenger.bbHeight + 0.2, z)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        if (tag.contains("Pulley")) configure(BlockPos.of(tag.getLong("Pulley")), tag.getFloat("HoseLength"))
        entityData.set(DOCK_ID, java.util.Optional.empty())
        entityData.set(DOCK_POSITION, java.util.Optional.empty())
        if (tag.hasUUID("DockId") && tag.contains("DockPosition")) {
            entityData.set(DOCK_ID, java.util.Optional.of(tag.getUUID("DockId")))
            entityData.set(DOCK_POSITION, java.util.Optional.of(BlockPos.of(tag.getLong("DockPosition"))))
        }
        if (tag.contains("WorldAnchorX")) {
            val saved = Vec3(tag.getDouble("WorldAnchorX"), tag.getDouble("WorldAnchorY"), tag.getDouble("WorldAnchorZ"))
            if (saved.x.isFinite() && saved.y.isFinite() && saved.z.isFinite()) lastWorldAnchor = saved
        }
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        if (!configured) return
        tag.putLong("Pulley", ownerPosition.asLong())
        tag.putFloat("HoseLength", hoseLength)
        dockId?.let { tag.putUUID("DockId", it) }
        dockPosition?.let { tag.putLong("DockPosition", it.asLong()) }
        lastWorldAnchor?.let { anchor ->
            tag.putDouble("WorldAnchorX", anchor.x)
            tag.putDouble("WorldAnchorY", anchor.y)
            tag.putDouble("WorldAnchorZ", anchor.z)
        }
    }

    override fun isPickable(): Boolean = carrier == null
    override fun isPushable(): Boolean = dockId == null

    companion object {
        const val END_ALLOWANCE = 0.35
        fun carriedBy(level: Level, player: Player): HoseHeadEntity? = level.getEntitiesOfClass(
            HoseHeadEntity::class.java, player.boundingBox.inflate(5.0)) { it.isAlive && it.isCarriedBy(player) }.singleOrNull()
        private val CARRIER: EntityDataAccessor<java.util.Optional<UUID>> =
            SynchedEntityData.defineId(HoseHeadEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val DOCK_POSITION: EntityDataAccessor<java.util.Optional<BlockPos>> =
            SynchedEntityData.defineId(HoseHeadEntity::class.java, EntityDataSerializers.OPTIONAL_BLOCK_POS)
        private val DOCK_ID: EntityDataAccessor<java.util.Optional<UUID>> =
            SynchedEntityData.defineId(HoseHeadEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val WRENCH = ResourceLocation.fromNamespaceAndPath("create", "wrench")
        private val HOSE_PULLEY = ResourceLocation.fromNamespaceAndPath("create", "hose_pulley")
        private val OWNER: EntityDataAccessor<BlockPos> =
            SynchedEntityData.defineId(HoseHeadEntity::class.java, EntityDataSerializers.BLOCK_POS)
        private val LENGTH: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(HoseHeadEntity::class.java, EntityDataSerializers.FLOAT)
    }
}
