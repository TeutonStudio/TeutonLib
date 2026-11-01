package de.TeutonStudio.TeutonLib.hosetest

import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.mojang.authlib.GameProfile
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import de.TeutonStudio.TeutonLib.HoseHeadMode
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.hose.HoseEntities
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import de.TeutonStudio.TeutonLib.integration.hose.HosePulleyHost
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import java.nio.file.Files
import java.util.UUID
import net.createmod.catnip.animation.LerpedFloat
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.entity.player.Player
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import kotlin.math.ceil

/** Exercises transformed Create block entities and actual Sable assembly in disposable worlds. */
@Mod("teutonlib_hose_test")
class HosePulleyVerification {
    init { NeoForge.EVENT_BUS.register(this); NeoForge.EVENT_BUS.register(HoseConnectorVerification()) }

    @SubscribeEvent
    fun commands(event: RegisterCommandsEvent) {
        val command = Commands.literal("teutonlib_hose_test").requires { it.hasPermission(4) }
        for (action in listOf("run", "checkpoint", "loaded", "cleanup", "mode-always", "mode-sable-only", "mode-never", "mode-status")) {
            command.then(Commands.literal(action).executes { context ->
                try {
                    val result = when (action) {
                        "run" -> verify(context.source.level)
                        "checkpoint" -> checkpoint(context.source.level)
                        "loaded" -> loaded(context.source.level)
                        "mode-status" -> modeStatus(context.source.level)
                        "mode-always", "mode-sable-only", "mode-never" -> {
                            ServerConfig.HOSE_HEAD_MODE.set(HoseHeadMode.valueOf(action.removePrefix("mode-").replace('-', '_').uppercase()))
                            modeStatus(context.source.level)
                        }
                        else -> { clear(context.source.level, ORIGIN); "PASS hose cleanup" }
                    }
                    context.source.sendSuccess({ Component.literal(result) }, false)
                    1
                } catch (error: Throwable) {
                    org.slf4j.LoggerFactory.getLogger("TeutonLibHoseVerification").error("Hose verification failed", error)
                    context.source.sendFailure(Component.literal("FAIL hose $action: $error"))
                    0
                }
            })
        }
        event.dispatcher.register(command)
    }

    companion object {
        private val ORIGIN = BlockPos(8, 220, 8)
        private val SABLE_ORIGIN = BlockPos(40, 220, 8)
        private val offsetField = HosePulleyBlockEntity::class.java.getDeclaredField("offset").apply { isAccessible = true }

        private fun offset(pulley: HosePulleyBlockEntity): LerpedFloat = offsetField.get(pulley) as LerpedFloat
        private fun length(pulley: HosePulleyBlockEntity, value: Float) { offset(pulley).startWithValue(value.toDouble()) }
        private fun drive(pulley: HosePulleyBlockEntity, speed: Float) {
            val previous = pulley.speed
            pulley.speed = speed
            pulley.onSpeedChanged(previous)
        }
        private fun host(pulley: HosePulleyBlockEntity): HosePulleyHost = pulley as HosePulleyHost
        private fun head(level: ServerLevel, pulley: HosePulleyBlockEntity): HoseHeadEntity =
            level.getEntity(checkNotNull(host(pulley).physicalHeadId) { "transformed pulley must assign a head UUID" }) as? HoseHeadEntity
                ?: error("physical head must be loaded")
        private fun center(head: HoseHeadEntity): Vec3 = head.position().add(0.0, head.bbHeight * 0.5, 0.0)
        private fun ownedHeads(level: ServerLevel, owner: BlockPos): List<HoseHeadEntity> =
            level.getEntitiesOfClass(HoseHeadEntity::class.java, AABB(ORIGIN).inflate(400.0)) { it.isAlive && it.belongsTo(owner) }
        private fun modeStatus(level: ServerLevel): String =
            "PASS hose mode=${ServerConfig.hoseHeadMode()} heads=${ownedHeads(level, ORIGIN).size}"

        private fun place(level: ServerLevel, position: BlockPos): HosePulleyBlockEntity {
            level.setChunkForced(position.x shr 4, position.z shr 4, true)
            level.getChunkAt(position)
            for (y in level.minBuildHeight..position.y) level.setBlockAndUpdate(BlockPos(position.x, y, position.z), Blocks.AIR.defaultBlockState())
            level.setBlockAndUpdate(position, AllBlocks.HOSE_PULLEY.defaultState)
            return level.getBlockEntity(position) as HosePulleyBlockEntity
        }

        private fun clear(level: ServerLevel, position: BlockPos) {
            level.getEntitiesOfClass(HoseHeadEntity::class.java, AABB(position).inflate(400.0)) { it.belongsTo(position) }
                .forEach { it.discard() }
            level.removeBlock(position, false)
        }

        private fun verify(level: ServerLevel): String {
            check(ServerConfig.hoseHeadMode() == HoseHeadMode.ALWAYS) { "hose verification requires the default ALWAYS mode" }
            clear(level, ORIGIN)
            val pulley = place(level, ORIGIN)
            check(pulley is HosePulleyHost) { "Create HosePulleyBlockEntity Mixin was not applied" }
            length(pulley, 5f)
            drive(pulley, 0f)
            pulley.tick()
            val physicalHead = head(level, pulley)
            val originalId = physicalHead.uuid
            repeat(80) { pulley.tick() }
            check(host(pulley).physicalHeadId == originalId) { "repeated pulley ticks changed head UUID" }
            check(level.getEntitiesOfClass(HoseHeadEntity::class.java, AABB(ORIGIN).inflate(400.0)) { it.belongsTo(ORIGIN) }.size == 1) {
                "pulley spawned duplicate heads"
            }

            val ownerNbt = pulley.saveWithFullMetadata(level.registryAccess())
            check(ownerNbt.hasUUID("TeutonLibHoseHead") && ownerNbt.getUUID("TeutonLibHoseHead") == originalId) {
                "pulley NBT must preserve the physical head UUID"
            }
            check(ownerNbt.contains("TeutonLibHoseHeadPosition")) { "pulley NBT must preserve the last head position" }
            val entityNbt = physicalHead.saveWithoutId(CompoundTag())
            val restored = checkNotNull(HoseEntities.HOSE_HEAD.get().create(level))
            restored.load(entityNbt)
            check(restored.belongsTo(ORIGIN) && restored.hoseLength == physicalHead.hoseLength) { "head NBT roundtrip lost owner/length" }
            verifyLava(level, pulley, physicalHead)

            level.setBlockAndUpdate(ORIGIN.below(4), Blocks.STONE.defaultBlockState())
            length(pulley, 6f)
            drive(pulley, 32f)
            pulley.tick()
            check(offset(pulley).value <= 3.001f) { "hose extended through an obstacle: ${offset(pulley).value}" }
            repeat(3) { pulley.lazyTick(); pulley.tick() }
            check(offset(pulley).value <= 3.001f) { "lazyTick restarted hose through obstacle" }
            level.removeBlock(ORIGIN.below(4), false)
            length(pulley, 6f)
            drive(pulley, -32f)
            val beforeRetraction = offset(pulley).value
            pulley.tick()
            check(offset(pulley).value < beforeRetraction) { "negative speed must retract the hose" }
            drive(pulley, 0f)
            val stopped = offset(pulley).value
            repeat(4) { pulley.tick() }
            check(offset(pulley).value == stopped) { "unpowered hose must remain stopped" }

            val worldBottom = (ORIGIN.y - level.minBuildHeight).toFloat()
            length(pulley, worldBottom + 20f)
            drive(pulley, 32f)
            repeat(4) { pulley.tick(); pulley.lazyTick() }
            check(offset(pulley).value in 0f..worldBottom) { "hose exceeded world bottom: ${offset(pulley).value}" }
            drive(pulley, 0f)
            length(pulley, 5f)
            pulley.tick()
            physicalHead.setPos(ORIGIN.x + 2.5, ORIGIN.y - 3.0, ORIGIN.z + .5)
            physicalHead.deltaMovement = Vec3(1.2, 0.0, .5)
            repeat(20) { physicalHead.tick() }
            val distance = center(physicalHead).distanceTo(physicalHead.worldAnchor())
            check(distance <= physicalHead.hoseLength + .351) { "head escaped its spherical length constraint: $distance" }
            check(physicalHead.y < ORIGIN.y) { "gravity must lower the physical head" }
            verifyInteractions(level, physicalHead)
            physicalHead.setHoseLength(Float.NaN)
            check(physicalHead.hoseLength == 0f) { "invalid hose length must not enter entity state" }
            level.removeBlock(ORIGIN, false)
            repeat(4) { physicalHead.tick() }
            check(physicalHead.isRemoved) { "removing the pulley must remove its physical head" }

            verifySable(level)
            verifyModes(level)
            return "PASS hose: transformed Create, spawn, UUID/no duplicates, lava/fire and saved Fire NBT, obstacle/lazyTick, retraction/stop, world bottom, physics/tether, wrench carry/throw/ride, NBT, owner removal, moved/rotated Sable anchor, native lava SIMULATE/EXECUTE at physical head before/after rotation, missing-head/dry-head stale-source exclusion and native filling, ALWAYS/SABLE_ONLY/NEVER live transitions and native fluid fallback"
        }

        private fun verifyLava(level: ServerLevel, pulley: HosePulleyBlockEntity, physicalHead: HoseHeadEntity) {
            val originalId = physicalHead.uuid
            val originalPosition = physicalHead.position()
            val originalVelocity = physicalHead.deltaMovement
            val basin = BlockPos.betweenClosed(ORIGIN.offset(-1, -6, -1), ORIGIN.offset(1, -2, 1))
                .associate { it.immutable() to level.getBlockState(it) }
            try {
                basin.keys.forEach { position ->
                    level.setBlockAndUpdate(position, if (position.y == ORIGIN.y - 6)
                        Blocks.STONE.defaultBlockState() else Blocks.LAVA.defaultBlockState())
                }
                physicalHead.setPos(ORIGIN.x + .5, ORIGIN.y - 3.5, ORIGIN.z + .5)
                physicalHead.deltaMovement = Vec3.ZERO
                physicalHead.clearFire()
                repeat(20) {
                    physicalHead.tick()
                    pulley.tick()
                    check(!physicalHead.isOnFire && !physicalHead.displayFireAnimation()) { "lava ignited the physical hose head" }
                }
                check(physicalHead.isInLava) { "lava regression did not immerse the physical head" }
                physicalHead.lavaHurt()
                check(!physicalHead.isOnFire && physicalHead.remainingFireTicks <= 0) { "native lavaHurt ignited the hose head" }

                // Existing saves can retain the positive Fire timer written before this fix.
                val burningSave = physicalHead.saveWithoutId(CompoundTag()).apply { putShort("Fire", 300.toShort()) }
                physicalHead.load(burningSave)
                check(physicalHead.remainingFireTicks == 300) { "legacy Fire NBT was not exercised" }
                check(!physicalHead.isOnFire && !physicalHead.displayFireAnimation()) { "loaded Fire NBT made the hose head burn" }
                physicalHead.tick()
                pulley.tick()
                check(physicalHead.remainingFireTicks < 300 && !physicalHead.isOnFire) { "loaded fire timer did not recover safely" }
                check(physicalHead.isAlive && physicalHead.uuid == originalId && head(level, pulley) === physicalHead &&
                    host(pulley).physicalHeadId == originalId && physicalHead.belongsTo(pulley.blockPos)) {
                    "lava or Fire NBT changed the physical head's identity or pulley binding"
                }
            } finally {
                basin.forEach { (position, state) -> level.setBlockAndUpdate(position, state) }
                physicalHead.clearFire()
                physicalHead.setPos(originalPosition.x, originalPosition.y, originalPosition.z)
                physicalHead.deltaMovement = originalVelocity
            }
        }

        private fun verifyInteractions(level: ServerLevel, head: HoseHeadEntity) {
            // NeoForge's FakePlayer intentionally refuses every startRiding call. A plain
            // Player subclass exercises Minecraft's real player/entity passenger handling.
            val player = object : Player(level, ORIGIN, 0f, GameProfile(UUID.randomUUID(), "HoseTest")) {
                override fun isSpectator(): Boolean = false
                override fun isCreative(): Boolean = false
            }
            player.stopRiding()
            player.setShiftKeyDown(false)
            player.setPos(head.x, head.y, head.z)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.STICK))
            check(head.interact(player, InteractionHand.MAIN_HAND) == InteractionResult.PASS) { "ordinary items must not operate the hose head" }
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack())
            head.setHoseLength(2f)
            check(head.interact(player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME) { "wrench must pick up a deployed hose head" }
            check(head.deltaMovement == Vec3.ZERO) { "carrying must reset head momentum" }
            head.interact(player, InteractionHand.MAIN_HAND)
            check(head.deltaMovement.dot(player.lookAngle) > .5) { "second wrench interaction must throw the held head forward" }
            head.setHoseLength(0f)
            head.interact(player, InteractionHand.MAIN_HAND)
            check(player.vehicle === head) { "wrench without shift at zero length must mount the head" }
            player.stopRiding()
            player.setShiftKeyDown(true)
            head.interact(player, InteractionHand.MAIN_HAND)
            check(player.vehicle == null && head.deltaMovement == Vec3.ZERO) { "shift-wrench at zero length must carry the head" }
            head.interact(player, InteractionHand.MAIN_HAND)
            check(head.deltaMovement.dot(player.lookAngle) > .5) { "shift-carried head must also be throwable" }
            player.setShiftKeyDown(false)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        }

        private fun verifySable(level: ServerLevel) {
            clear(level, SABLE_ORIGIN)
            val sourcePulley = place(level, SABLE_ORIGIN)
            length(sourcePulley, 5f)
            drive(sourcePulley, 0f)
            sourcePulley.tick()
            val originalHead = head(level, sourcePulley)
            val originalId = originalHead.uuid
            val ship = SubLevelAssemblyHelper.assembleBlocks(level, SABLE_ORIGIN, java.util.List.of(SABLE_ORIGIN), BoundingBox3i(SABLE_ORIGIN, SABLE_ORIGIN))
            val ownerPosition = ship.plot.centerBlock
            val pulley = level.getBlockEntity(ownerPosition) as HosePulleyBlockEntity
            val container = checkNotNull(SubLevelContainer.getContainer(level))
            try {
                check(host(pulley).physicalHeadId == originalId) { "assembly must migrate the existing head UUID in pulley NBT" }
                // Entity ticks may precede the assembled owner's first block-entity tick.
                originalHead.tick()
                check(!originalHead.isRemoved) { "assembly grace period must allow an entity tick before its new owner rebinds it" }
                pulley.tick()
                val physicalHead = head(level, pulley)
                check(physicalHead === originalHead && physicalHead.uuid == originalId) { "assembly must reuse the existing physical head" }
                check(physicalHead.belongsTo(ownerPosition)) { "assembly must rebind the head to its new storage position" }
                val local = Vec3(ownerPosition.x + .5, ownerPosition.y.toDouble(), ownerPosition.z + .5)
                val before = physicalHead.worldAnchor()
                check(before.distanceToSqr(ship.logicalPose().transformPosition(local)) < 1.0e-8) { "assembled head anchor must use world coordinates" }
                check(physicalHead.position().distanceTo(before) < 2.0) { "assembled head spawned in storage coordinates" }
                verifyFluidTransfer(level, pulley, physicalHead, ship.logicalPose(), "assembled")
                val target = Pose3d(ship.logicalPose())
                target.position().add(12.0, 7.0, -4.0)
                target.orientation().rotateY(Math.PI / 2).rotateZ(Math.PI / 6)
                ship.logicalPose().set(target)
                RigidBodyHandle.of(ship)?.teleport(target.position(), target.orientation())
                SubLevelPhysicsSystem.require(level).pipeline.resetVelocity(ship)
                val moved = physicalHead.worldAnchor()
                check(moved.distanceToSqr(target.transformPosition(local)) < 1.0e-8) { "moved/rotated head anchor must follow the ship pose" }
                check(moved.distanceTo(before) > 5.0) { "fixture must actually move the ship anchor" }
                repeat(8) { pulley.tick(); physicalHead.tick() }
                check(host(pulley).physicalHeadId == physicalHead.uuid) { "ship movement must not duplicate the head" }
                check(center(physicalHead).distanceTo(physicalHead.worldAnchor()) <= physicalHead.hoseLength + .351) {
                    "head must remain tethered after ship movement and rotation"
                }
                verifyFluidTransfer(level, pulley, physicalHead, ship.logicalPose(), "moved/rotated")
                val adapterClass = Class.forName("de.TeutonStudio.TeutonLib.integration.sable.SableHoseOwnership")
                val adapter = adapterClass.getField("INSTANCE").get(null)
                val ownerRemoved = adapterClass.getMethod("ownerRemoved", net.minecraft.world.level.Level::class.java, BlockPos::class.java)
                fun removed(): Boolean = ownerRemoved.invoke(adapter, level, ownerPosition) as Boolean
                check(!removed()) { "occupied owner plot must remain valid" }
                val plotX = ship.plot.plotPos.x - container.origin.x
                val plotZ = ship.plot.plotPos.z - container.origin.y
                val plotIndex = container.getIndex(plotX, plotZ)
                container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED)
                check(removed()) { "permanently released plot must report its owner as removed" }
                // UNLOADED leaves exactly this occupancy marker set while clearing loaded plot
                // lookup. Exercise that adapter distinction without unloading the test's world.
                container.occupancy.set(plotIndex)
                try {
                    check(!removed()) { "occupied but unloaded owner plot must not be treated as removed" }
                } finally {
                    container.occupancy.clear(plotIndex)
                }
                repeat(4) { physicalHead.tick() }
                check(physicalHead.isRemoved) { "removing the whole sublevel must remove its head without a separate block removal" }
            } finally {
                host(pulley).physicalHeadId?.let(level::getEntity)?.discard()
                if (container.getSubLevel(ship.uniqueId) != null) {
                    level.removeBlock(ownerPosition, false)
                    container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED)
                }
            }
        }

        /** Exercise Create's real registered capability and behaviour search ticks, with no fluid stubs. */
        private fun verifyFluidTransfer(level: ServerLevel, pulley: HosePulleyBlockEntity, physicalHead: HoseHeadEntity,
            pose: Pose3d, phase: String) {
            val originalPosition = physicalHead.position()
            val originalVelocity = physicalHead.deltaMovement
            val originalLength = offset(pulley).value
            val saved = linkedMapOf<BlockPos, BlockState>()
            fun replace(position: BlockPos, state: BlockState) {
                val immutable = position.immutable()
                saved.putIfAbsent(immutable, level.getBlockState(immutable))
                level.setBlock(immutable, state, 2)
            }
            fun basin(cell: BlockPos, filled: Boolean) {
                level.setChunkForced(cell.x shr 4, cell.z shr 4, true)
                for (position in BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, 1, 1))) {
                    replace(position, if (position == cell) {
                        if (filled) Blocks.LAVA.defaultBlockState() else Blocks.AIR.defaultBlockState()
                    } else Blocks.STONE.defaultBlockState())
                }
            }
            fun moveHead(cell: BlockPos) {
                physicalHead.setPos(cell.x + .5, cell.y + .1, cell.z + .5)
                physicalHead.deltaMovement = Vec3.ZERO
                check(physicalHead.blockPosition() == cell)
                check(center(physicalHead).distanceTo(physicalHead.worldAnchor()) <= physicalHead.hoseLength + .351) {
                    "$phase fluid fixture put the physical head outside its tether"
                }
            }
            fun source(cell: BlockPos): Boolean = level.getFluidState(cell).let { it.type === Fluids.LAVA && it.isSource }
            try {
                drive(pulley, 0f)
                length(pulley, 5f)
                pulley.tick()
                val anchor = physicalHead.worldAnchor()
                val legacyRoot = BlockPos.containing(pose.transformPosition(
                    Vec3.atCenterOf(pulley.blockPos.below(ceil(offset(pulley).value.toDouble()).toInt()))))
                // Keep both nozzles inside the five-block tether. Choose around the actual pose
                // because rotating local down can otherwise land on one of these world cells.
                val targets = listOf(Vec3(2.0, -4.0, 0.0), Vec3(-2.0, -4.0, 0.0),
                    Vec3(0.0, -4.0, 2.0), Vec3(0.0, -4.0, -2.0))
                    .map { BlockPos.containing(anchor.add(it)) }.filter { it.distManhattan(legacyRoot) > 1 }
                val sourceCell = targets.first()
                val dryCell = targets.first { it.distManhattan(sourceCell) > 2 }
                basin(sourceCell, true)
                basin(dryCell, false)
                replace(legacyRoot, Blocks.AIR.defaultBlockState())
                moveHead(sourceCell)
                val handler = checkNotNull(level.getCapability(Capabilities.FluidHandler.BLOCK, pulley.blockPos, null)) {
                    "$phase mounted pulley lost its native fluid capability"
                }
                check(handler.javaClass.name == "com.simibubi.create.content.fluids.hosePulley.HosePulleyFluidHandler") {
                    "$phase test must exercise Create's real HosePulleyFluidHandler"
                }
                val beforeSimulation = saved.keys.associateWith(level::getBlockState)
                var simulated = FluidStack.EMPTY
                for (tick in 0 until 800) {
                    simulated = handler.drain(1000, FluidAction.SIMULATE)
                    check(source(sourceCell)) { "$phase simulation consumed the finite lava source" }
                    if (simulated.amount == 1000) break
                    pulley.tick()
                }
                check(simulated.fluid === Fluids.LAVA && simulated.amount == 1000) {
                    "$phase physical head could not simulate one bucket of lava; head=$sourceCell legacy=$legacyRoot result=$simulated"
                }
                check(beforeSimulation.all { (position, state) -> level.getBlockState(position) == state }) {
                    "$phase SIMULATE changed a basin block"
                }
                // A valid native search cache must not outlive the physical head binding. Do not
                // tick the pulley under the temporary UUID: that would exercise head respawning.
                val validHeadId = checkNotNull(host(pulley).physicalHeadId)
                try {
                    host(pulley).physicalHeadId = UUID.randomUUID()
                    check(handler.drain(1000, FluidAction.SIMULATE).isEmpty) { "$phase missing head simulated lava from a stale search cache" }
                    check(handler.drain(1000, FluidAction.EXECUTE).isEmpty) { "$phase missing head drained lava from a stale search cache" }
                    check(source(sourceCell) && beforeSimulation.all { (position, state) -> level.getBlockState(position) == state }) {
                        "$phase missing-head check changed world fluid"
                    }
                } finally {
                    host(pulley).physicalHeadId = validHeadId
                }
                val drained = handler.drain(1000, FluidAction.EXECUTE)
                check(drained.fluid === Fluids.LAVA && drained.amount == 1000) { "$phase native EXECUTE did not return one lava bucket: $drained" }
                check(level.getFluidState(sourceCell).isEmpty) { "$phase native EXECUTE did not remove the finite source" }
                check(level.getFluidState(legacyRoot).isEmpty) { "$phase drain altered the dry legacy offset target" }

                // Keep the previous source available while moving the head to dry air. Cached searches
                // and Sable's previous lastValidPos must not continue collecting from that old source.
                replace(sourceCell, Blocks.LAVA.defaultBlockState())
                moveHead(dryCell)
                repeat(80) {
                    pulley.tick()
                    check(handler.drain(1000, FluidAction.SIMULATE).isEmpty) { "$phase dry physical head simulated fluid from its previous target" }
                    check(handler.drain(1000, FluidAction.EXECUTE).isEmpty) { "$phase dry physical head drained its previous target" }
                    check(source(sourceCell) && level.getFluidState(dryCell).isEmpty) { "$phase dry-head check changed either basin" }
                }

                // The matching native filling path must place fluid at the real head too. Simulation
                // advances only Create's own context/search machinery; it must leave both cells alone.
                repeat(800) {
                    handler.fill(FluidStack(Fluids.LAVA, 1000), FluidAction.SIMULATE)
                    pulley.tick()
                    check(level.getFluidState(dryCell).isEmpty && source(sourceCell)) { "$phase filling simulation changed world fluid" }
                }
                val inserted = handler.fill(FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE)
                check(inserted == 1000 && source(dryCell)) { "$phase native filling did not place a lava source at the physical head: accepted=$inserted" }
                check(source(sourceCell) && level.getFluidState(legacyRoot).isEmpty) { "$phase filling used the old source/offset instead of the head" }
            } finally {
                saved.forEach { (position, state) -> level.setBlock(position, state, 2) }
                physicalHead.setPos(originalPosition.x, originalPosition.y, originalPosition.z)
                physicalHead.deltaMovement = originalVelocity
                length(pulley, originalLength)
                pulley.tick()
            }
        }

        private fun verifyModes(level: ServerLevel) {
            ServerConfig.HOSE_HEAD_MODE.set(HoseHeadMode.ALWAYS)
            clear(level, ORIGIN)
            clear(level, SABLE_ORIGIN)
            val worldPulley = place(level, ORIGIN)
            val sourcePulley = place(level, SABLE_ORIGIN)
            for (pulley in listOf(worldPulley, sourcePulley)) {
                length(pulley, 5f)
                drive(pulley, 0f)
                pulley.tick()
            }
            val ship = SubLevelAssemblyHelper.assembleBlocks(level, SABLE_ORIGIN,
                java.util.List.of(SABLE_ORIGIN), BoundingBox3i(SABLE_ORIGIN, SABLE_ORIGIN))
            val owner = ship.plot.centerBlock
            val mounted = level.getBlockEntity(owner) as HosePulleyBlockEntity
            val container = checkNotNull(SubLevelContainer.getContainer(level))
            fun settle() = repeat(4) {
                worldPulley.tick()
                mounted.tick()
                (ownedHeads(level, ORIGIN) + ownedHeads(level, owner)).forEach { it.tick() }
            }
            fun expect(worldCount: Int, mountedCount: Int) {
                check(ownedHeads(level, ORIGIN).size == worldCount) { "${ServerConfig.hoseHeadMode()} produced the wrong ordinary-world head count" }
                check(ownedHeads(level, owner).size == mountedCount) { "${ServerConfig.hoseHeadMode()} produced the wrong Sable head count" }
                check(host(worldPulley).physicalHeadEnabled == (worldCount == 1)) { "world owner did not synchronize the head policy flag" }
                check(host(mounted).physicalHeadEnabled == (mountedCount == 1)) { "Sable owner did not synchronize the head policy flag" }
            }
            try {
                settle(); expect(1, 1)
                val firstWorld = head(level, worldPulley)
                val firstMounted = head(level, mounted)
                ServerConfig.HOSE_HEAD_MODE.set(HoseHeadMode.SABLE_ONLY)
                settle(); expect(0, 1)
                check(firstWorld.isRemoved && head(level, mounted) === firstMounted) { "SABLE_ONLY did not remove only the loaded world head" }
                verifyNativeFluidWithoutHead(level, worldPulley, null, "SABLE_ONLY world")

                ServerConfig.HOSE_HEAD_MODE.set(HoseHeadMode.NEVER)
                settle(); expect(0, 0)
                check(firstMounted.isRemoved) { "NEVER left a loaded Sable head behind" }
                verifyNativeFluidWithoutHead(level, worldPulley, null, "NEVER world")
                verifyNativeFluidWithoutHead(level, mounted, ship.logicalPose(), "NEVER Sable")

                ServerConfig.HOSE_HEAD_MODE.set(HoseHeadMode.ALWAYS)
                settle(); expect(1, 1)
                val secondWorld = head(level, worldPulley)
                val secondMounted = head(level, mounted)
                check(secondWorld.uuid != firstWorld.uuid && secondMounted.uuid != firstMounted.uuid) { "re-enabling reused a discarded head entity" }
                repeat(20) { settle(); expect(1, 1) }
                ServerConfig.HOSE_HEAD_MODE.set(HoseHeadMode.NEVER)
                settle(); expect(0, 0)
                check(secondWorld.isRemoved && secondMounted.isRemoved)
                ServerConfig.HOSE_HEAD_MODE.set(HoseHeadMode.ALWAYS)
                settle(); expect(1, 1)
            } finally {
                ServerConfig.HOSE_HEAD_MODE.set(HoseHeadMode.ALWAYS)
                clear(level, ORIGIN)
                ownedHeads(level, owner).forEach { it.discard() }
                if (container.getSubLevel(ship.uniqueId) != null) {
                    level.removeBlock(owner, false)
                    container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED)
                }
            }
        }

        /** Disabled entity modes must preserve Create/Sable's original offset-based intake. */
        private fun verifyNativeFluidWithoutHead(level: ServerLevel, pulley: HosePulleyBlockEntity, pose: Pose3d?, phase: String) {
            check(ownedHeads(level, pulley.blockPos).isEmpty()) { "$phase unexpectedly has a physical head" }
            length(pulley, 5f)
            drive(pulley, 0f)
            pulley.tick()
            val localRoot = pulley.blockPos.below(ceil(offset(pulley).value.toDouble()).toInt())
            val cell = if (pose == null) localRoot else BlockPos.containing(pose.transformPosition(Vec3.atCenterOf(localRoot)))
            level.setChunkForced(cell.x shr 4, cell.z shr 4, true)
            val saved = BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, 1, 1))
                .associate { it.immutable() to level.getBlockState(it) }
            try {
                for (position in saved.keys) level.setBlock(position, when (position) {
                    cell -> Blocks.LAVA.defaultBlockState()
                    cell.above() -> Blocks.AIR.defaultBlockState() // Keep the native hose's vertical path clear.
                    else -> Blocks.STONE.defaultBlockState()
                }, 2)
                val handler = checkNotNull(level.getCapability(Capabilities.FluidHandler.BLOCK, pulley.blockPos, null))
                var simulated = FluidStack.EMPTY
                for (tick in 0 until 800) {
                    simulated = handler.drain(1000, FluidAction.SIMULATE)
                    check(level.getFluidState(cell).isSource) { "$phase native simulation consumed its source" }
                    if (simulated.amount == 1000) break
                    pulley.tick()
                }
                check(simulated.fluid === Fluids.LAVA && simulated.amount == 1000) { "$phase disabled physical head broke native fluid simulation" }
                val actual = handler.drain(1000, FluidAction.EXECUTE)
                check(actual.fluid === Fluids.LAVA && actual.amount == 1000 && level.getFluidState(cell).isEmpty) {
                    "$phase disabled physical head broke native fluid extraction"
                }
                check(ownedHeads(level, pulley.blockPos).isEmpty()) { "$phase fluid requests spawned a disabled physical head" }
            } finally {
                saved.forEach { (position, state) -> level.setBlock(position, state, 2) }
            }
        }

        private fun checkpoint(level: ServerLevel): String {
            clear(level, ORIGIN)
            val pulley = place(level, ORIGIN)
            length(pulley, 4f)
            drive(pulley, 0f)
            pulley.tick()
            val physicalHead = head(level, pulley)
            repeat(4) { physicalHead.tick(); pulley.tick() }
            pulley.setChanged()
            Files.writeString(level.server.getWorldPath(LevelResource.ROOT).resolve("teutonlib-hose-checkpoint.txt"), physicalHead.uuid.toString())
            return "PASS hose checkpoint ${physicalHead.uuid}"
        }

        private fun loaded(level: ServerLevel): String {
            level.setChunkForced(ORIGIN.x shr 4, ORIGIN.z shr 4, true)
            level.getChunkAt(ORIGIN)
            val expected = UUID.fromString(Files.readString(level.server.getWorldPath(LevelResource.ROOT).resolve("teutonlib-hose-checkpoint.txt")).trim())
            val pulley = level.getBlockEntity(ORIGIN) as? HosePulleyBlockEntity ?: error("saved pulley was not restored")
            check(host(pulley).physicalHeadId == expected) { "restart changed persisted head UUID" }
            val physicalHead = level.getEntity(expected) as? HoseHeadEntity ?: return "RUNNING hose restart: waiting for saved entity chunk"
            check(physicalHead.belongsTo(ORIGIN)) { "restart lost the head owner" }
            check(level.getEntitiesOfClass(HoseHeadEntity::class.java, AABB(ORIGIN).inflate(400.0)) { it.belongsTo(ORIGIN) }.size == 1) {
                "restart created duplicate physical heads"
            }
            return "PASS hose restart: saved pulley/head UUID, owner and exactly one entity restored"
        }
    }
}
