package de.TeutonStudio.TeutonLib.traintest

import com.google.gson.Gson
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.simibubi.create.AllBlocks
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers
import de.TeutonStudio.TeutonLib.integration.cso.docking.AnalogDocking
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity as Dock
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair
import dev.simulated_team.simulated.index.SimBlocks
import dev.simulated_team.simulated.multiloader.tanks.CFluidType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.properties.BlockStateProperties as Props
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.Vec3
import qouteall.imm_ptl.core.portal.Portal
import java.nio.file.Files
import java.util.UUID

/** Native docking acceptance fixture for the two-wagon film set.
 * The companion is a genuinely dynamic Sable body: only the connector's fixed
 * constraint carries it while the train moves. No per-tick fixture pose updates.
 */
object TrainDockingDemo {
    private const val MARKER = "teutonlib:docking_demo"
    private val gson = Gson()
    private val rootOffset = BlockPos(4, 2, 2)
    private var case: String? = null
    private var carrierId: UUID? = null
    private var rootId: UUID? = null
    private var trainIds = emptySet<UUID>()
    private var carrierDockOffset = BlockPos.ZERO
    private var carrierChestOffset = BlockPos.ZERO
    private var initialHandle: FixedConstraintHandle? = null
    private var checkedTransfers = false
    private var sourceVerified = false
    private var targetVerified = false
    private var carrierStart: Vec3? = null
    private var rootStart: Vec3? = null
    private var nativeCarrierTravel = 0.0
    private var nativeRootTravel = 0.0
    private var rollbackVerified = false
    private var captureLabel: String? = null
    private var captureRemaining = 0
    private val captureSamples = mutableListOf<Map<String, Any>>()
    private var massProfile = "normal"
    private var initialBaseSeparation = 3
    private var weightStudy = false
    private var preparedGeometry: Map<String, Any> = emptyMap()
    private val handleField = Dock::class.java.getDeclaredField("constraintHandle").apply { isAccessible = true }

    val portalWidth: Double get() = when (case) { "wide" -> 30.0; "narrow" -> 9.0; else -> 16.0 }

    fun commands(root: LiteralArgumentBuilder<CommandSourceStack>) {
        val dock = Commands.literal("dock")
        for (name in listOf("wide", "narrow")) dock.then(Commands.literal(name).executes { ctx ->
            execute(ctx.source) { prepare(name) }
        })
        root.then(dock)
        root.then(Commands.literal("dock-fixture").then(Commands.argument("mass", StringArgumentType.word())
            .then(Commands.argument("distance", IntegerArgumentType.integer(2, 4)).executes { ctx ->
                execute(ctx.source) {
                    check(case == null) { "Choose the fixture before creating the docking body" }
                    val profile = StringArgumentType.getString(ctx, "mass")
                    require(profile in listOf("light", "normal", "heavy")) { "Use light, normal or heavy" }
                    massProfile = profile
                    initialBaseSeparation = IntegerArgumentType.getInteger(ctx, "distance")
                    weightStudy = true
                }
            })))
        root.then(Commands.literal("dock-status").executes { ctx -> execute(ctx.source) {} })
        val hybrid = Commands.literal("dock-hybrid")
        for (enabled in listOf(true, false)) hybrid.then(Commands.literal(enabled.toString()).executes { ctx ->
            execute(ctx.source) {
                check(captureRemaining == 0) { "Wait for the current sag capture before changing the configuration" }
                ServerConfig.HYBRID_TRAINS.set(enabled)
                check(ServerConfig.hybridTrains() == enabled)
            }
        })
        root.then(hybrid)
        root.then(Commands.literal("dock-sag-start").then(Commands.argument("label", StringArgumentType.word())
            .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200)).executes { ctx ->
                execute(ctx.source) {
                    check(case != null && captureRemaining == 0) { "Prepare the dock and finish the previous capture first" }
                    val label = StringArgumentType.getString(ctx, "label")
                    require(label.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "Use a simple filename label" }
                    captureLabel = label
                    captureRemaining = IntegerArgumentType.getInteger(ctx, "ticks")
                    captureSamples.clear()
                }
            })))
        root.then(Commands.literal("dock-rollback").executes { ctx -> execute(ctx.source) { rollback() } })
        val verification = Commands.literal("dock-verify")
        for (stage in listOf("source", "target")) verification.then(Commands.literal(stage).executes { ctx ->
            execute(ctx.source) { verify(stage) }
        })
        root.then(verification)
    }

    private fun execute(source: CommandSourceStack, action: () -> Unit): Int = try {
        action()
        source.sendSuccess({ Component.literal(status()) }, false)
        1
    } catch (error: Throwable) {
        org.slf4j.LoggerFactory.getLogger("TeutonLib/TrainDockingDemo").error("Docking demo failed", error)
        runCatching {
            val report = TrainDemo.sourceLevel().server.getWorldPath(LevelResource.ROOT).resolve("train-docking-verification")
            Files.createDirectories(report)
            Files.writeString(report.resolve("failure.json"), gson.toJson(mapOf("error" to error.stackTraceToString(), "state" to gson.fromJson(status(), Any::class.java))))
        }
        source.sendFailure(Component.literal("FAIL $error"))
        0
    }

    private fun prepare(name: String) {
        check(case == null) { "Use a fresh disposable world for each docking case" }
        val roots = TrainDemo.roots().map { it.second }
        val root = roots.single { it.level.getBlockState(it.plot.centerBlock.offset(2, 3, 0)).`is`(AllBlocks.MECHANICAL_BEARING.get()) }
        val level = root.level
        trainIds = roots.map { it.uniqueId }.toSet()
        rootId = root.uniqueId
        case = name
        val marker = UUID.randomUUID().toString()
        val positions = mutableListOf<BlockPos>()
        fun world(offset: BlockPos): BlockPos = BlockPos.containing(root.logicalPose().transformPosition(root.plot.centerBlock.offset(offset).center))
        fun place(offset: BlockPos, state: net.minecraft.world.level.block.state.BlockState) {
            val pos = world(offset)
            check(level.getBlockState(pos).isAir) { "Companion area occupied at $pos" }
            check(level.setBlockAndUpdate(pos, state))
            positions.add(pos)
        }
        // A broad magenta loading platform sits beside the cyan wagon. Its outer
        // edge is visibly outside the narrow portal but inside the wide portal.
        val shift = initialBaseSeparation - 3
        val nearZ = 5 + shift
        val farZ = 11 + shift
        for (x in 1..7) for (z in nearZ..farZ) {
            val material = when (massProfile) {
                "light" -> Blocks.MAGENTA_WOOL
                "heavy" -> Blocks.IRON_BLOCK
                else -> if (x in listOf(1, 7) || z in listOf(nearZ, farZ)) Blocks.MAGENTA_CONCRETE else Blocks.DARK_OAK_PLANKS
            }
            place(BlockPos(x, 1, z), material.defaultBlockState())
        }
        for (x in listOf(1, 7)) for (z in listOf(nearZ, farZ)) {
            place(BlockPos(x, 2, z), Blocks.MAGENTA_CONCRETE.defaultBlockState())
            place(BlockPos(x, 3, z), Blocks.SEA_LANTERN.defaultBlockState())
        }
        val towardWagon = root.logicalPose().transformNormal(Vec3(0.0, 0.0, -1.0))
        val peerFacing = Direction.getNearest(towardWagon.x.toFloat(), towardWagon.y.toFloat(), towardWagon.z.toFloat())
        val worldDock = world(BlockPos(4, 2, nearZ))
        // The optional mass study uses the same full-cube vanilla signal source
        // for HALF and FULL so switching arm length does not change body mass.
        // Initialize its output after assembly: TargetBlock resets a newly
        // placed nonzero state, but a subsequent same-block state change is a
        // persistent native analog source without a scheduled projectile tick.
        level.setBlockAndUpdate(world(BlockPos(4, 1, nearZ)),
            (if (weightStudy) Blocks.TARGET else Blocks.REDSTONE_BLOCK).defaultBlockState())
        place(BlockPos(4, 2, nearZ), SimBlocks.DOCKING_CONNECTOR.defaultState.setValue(Props.FACING, peerFacing).setValue(Props.POWERED, true))
        val worldChest = world(BlockPos(4, 2, 9 + shift))
        place(BlockPos(4, 2, 9 + shift), Blocks.CHEST.defaultBlockState())
        (level.getBlockEntity(worldChest) as ChestBlockEntity).setItem(0, ItemStack(Items.EMERALD, 23))
        val carrier = SubLevelAssemblyHelper.assembleBlocks(level, worldDock, positions, BoundingBox3i.from(positions))
        carrier.name = "TeutonLib · magenta docking platform ($name)"
        carrier.userDataTag = (carrier.userDataTag?.copy() ?: CompoundTag()).apply { putString(MARKER, marker) }
        carrierId = carrier.uniqueId
        carrierDockOffset = BlockPos.containing(carrier.logicalPose().transformPositionInverse(worldDock.center)).subtract(carrier.plot.centerBlock)
        carrierChestOffset = BlockPos.containing(carrier.logicalPose().transformPositionInverse(worldChest.center)).subtract(carrier.plot.centerBlock)
        HybridTrainRuntime.maintain(root) {
            level.setBlockAndUpdate(root.plot.centerBlock.offset(rootOffset.below()),
                (if (weightStudy) Blocks.TARGET else Blocks.REDSTONE_BLOCK).defaultBlockState())
            level.setBlockAndUpdate(root.plot.centerBlock.offset(rootOffset), SimBlocks.DOCKING_CONNECTOR.defaultState
                .setValue(Props.FACING, Direction.SOUTH).setValue(Props.POWERED, true))
        }
        val (a, b) = connectors()
        if (weightStudy) {
            val signal = if (initialBaseSeparation == 2) 8 else 15
            HybridTrainRuntime.maintain(root) {
                for (dock in listOf(a, b)) check(level.setBlockAndUpdate(dock.blockPos.below(), Blocks.TARGET.defaultBlockState().setValue(Props.POWER, signal)))
            }
            check(level.getBestNeighborSignal(a.blockPos) == signal && level.getBestNeighborSignal(b.blockPos) == signal) { "Native analog signal source initialization failed" }
        }
        // Advance the actual block entities to their requested native extension and
        // ask the native pair to dock; it creates the real fixed constraint.
        HybridTrainRuntime.maintain(root) {
            repeat(40) {
                a.tick(); b.tick()
                (Dock.MAGNET_CONTROLLER.getPair(level, a.blockPos, b.blockPos) as? DockingConnectorPair)?.dock(false)
            }
        }
        preparedGeometry = sagMeasurement(a, b, root, carrier).filterKeys { it in setOf(
            "baseDistance", "tipDistance", "baseDeltaY", "tipDeltaY", "trainSignal", "platformSignal",
            "trainExtension", "platformExtension", "trainEffectiveLength", "platformEffectiveLength",
            "trainLocked", "platformLocked", "validConstraints") }
        check((weightStudy && initialBaseSeparation == 4) || (a.isLocked && b.isLocked && a.otherConnector === b && b.otherConnector === a)) {
            "Native connectors did not lock: A=${a.saveWithFullMetadata(level.registryAccess())} B=${b.saveWithFullMetadata(level.registryAccess())}"
        }
        initialHandle = (if (weightStudy && initialBaseSeparation == 4) handles(a, b).singleOrNull()
            else handles(a, b).single()).also { if (it != null) check(it.isValid) }
        carrierStart = Vec3.atLowerCornerOf(carrier.plot.centerBlock).let(carrier.logicalPose()::transformPosition)
        rootStart = Vec3.atLowerCornerOf(root.plot.centerBlock).let(root.logicalPose()::transformPosition)
    }

    /** Observation only; native docking physics owns all companion movement. */
    fun observe() {
        if (case == null || carrierStart == null) return
        val root = root(); val carrier = carrier()
        val (a, b) = connectors()
        if (root.level === TrainDemo.sourceLevel() && carrier.level === root.level && a.isLocked && b.isLocked && handles(a, b).any { it.isValid }) {
            nativeCarrierTravel = maxOf(nativeCarrierTravel, carrier.logicalPose().transformPosition(Vec3.atLowerCornerOf(carrier.plot.centerBlock)).distanceTo(carrierStart!!))
            nativeRootTravel = maxOf(nativeRootTravel, root.logicalPose().transformPosition(Vec3.atLowerCornerOf(root.plot.centerBlock)).distanceTo(rootStart!!))
        }
        if (captureRemaining > 0) {
            captureSamples.add(sagMeasurement(a, b, root, carrier))
            captureRemaining--
            if (captureRemaining == 0) writeSagCapture()
        }
    }

    /** Read the two exact anchor positions used by Simulated's native fixed joint. */
    private fun sagMeasurement(a: Dock, b: Dock, root: ServerSubLevel, carrier: ServerSubLevel): Map<String, Any> {
        fun vector(value: Vec3) = listOf(value.x, value.y, value.z)
        fun normal(dock: Dock, body: ServerSubLevel): Vec3 = body.logicalPose().transformNormal(
            Vec3.atLowerCornerOf(dock.blockState.getValue(Props.FACING).normal)).normalize()
        fun angle(a: Vec3, b: Vec3) = Math.toDegrees(kotlin.math.acos(a.dot(b).coerceIn(-1.0, 1.0)))
        val rootBase = root.logicalPose().transformPosition(a.blockPos.center)
        val carrierBase = carrier.logicalPose().transformPosition(b.blockPos.center)
        val rootTip = root.logicalPose().transformPosition(a.tipPosition)
        val carrierTip = carrier.logicalPose().transformPosition(b.tipPosition)
        val delta = carrierTip.subtract(rootTip)
        val rootUp = root.logicalPose().transformNormal(Vec3(0.0, 1.0, 0.0)).normalize()
        val carrierUp = carrier.logicalPose().transformNormal(Vec3(0.0, 1.0, 0.0)).normalize()
        carrier.forceUpdateGlobalBounds()
        return linkedMapOf("gameTime" to root.level.gameTime, "hybridEnabled" to ServerConfig.hybridTrains(),
            "sameDimension" to (root.level === carrier.level),
            "trainBase" to vector(rootBase), "platformBase" to vector(carrierBase),
            "trainTip" to vector(rootTip), "platformTip" to vector(carrierTip),
            "baseDeltaY" to (carrierBase.y - rootBase.y), "tipDeltaY" to delta.y,
            "baseDistance" to carrierBase.distanceTo(rootBase),
            "platformMinimumY" to carrier.boundingBox().minY(),
            "tipDistance" to delta.length(), "tipHorizontalDistance" to kotlin.math.hypot(delta.x, delta.z),
            "trainExtension" to a.extension.value, "platformExtension" to b.extension.value,
            "trainSignal" to root.level.getBestNeighborSignal(a.blockPos), "platformSignal" to carrier.level.getBestNeighborSignal(b.blockPos),
            "trainEffectiveLength" to AnalogDocking.access(a).effectiveLength.name,
            "platformEffectiveLength" to AnalogDocking.access(b).effectiveLength.name,
            "opposingAngleDegrees" to angle(normal(a, root), normal(b, carrier).scale(-1.0)),
            "relativeTiltDegrees" to angle(rootUp, carrierUp),
            "trainLocked" to a.isLocked, "platformLocked" to b.isLocked,
            "validConstraints" to handles(a, b).count { it.isValid })
    }

    private fun carriageStatus(): List<Map<String, String>> = TrainDemo.roots().map { (link, body) ->
        val owner = body.level.getEntity(link.entityId) as CarriageContraptionEntity
        mapOf("entity" to owner.uuid.toString(), "body" to body.uniqueId.toString(),
            "class" to owner.contraption.javaClass.name, "type" to owner.contraption.type.toString())
    }

    private fun writeSagCapture() {
        val statistics = listOf("baseDeltaY", "tipDeltaY", "baseDistance", "platformMinimumY", "tipDistance", "tipHorizontalDistance", "opposingAngleDegrees", "relativeTiltDegrees")
            .associateWith { metric ->
                val values = captureSamples.map { (it.getValue(metric) as Number).toDouble() }
                mapOf("min" to values.min(), "max" to values.max(), "mean" to values.average(), "last" to values.last())
            }
        val report = TrainDemo.sourceLevel().server.getWorldPath(LevelResource.ROOT).resolve("train-docking-verification")
        Files.createDirectories(report)
        Files.writeString(report.resolve("sag-$captureLabel.json"), gson.toJson(linkedMapOf(
            "label" to captureLabel, "ticks" to captureSamples.size, "hybridEnabled" to ServerConfig.hybridTrains(),
            "carriages" to carriageStatus(), "weightStudy" to weightStudyStatus(), "summary" to statistics, "samples" to captureSamples)))
    }

    private fun bodies(): List<ServerSubLevel> = TrainDemo.sourceLevel().server.allLevels.flatMap {
        SubLevelContainer.getContainer(it)?.allSubLevels.orEmpty().filterIsInstance<ServerSubLevel>()
    }

    private fun carrier() = bodies().single { it.uniqueId == carrierId }
    private fun root() = bodies().single { it.uniqueId == rootId }
    private fun connectors(): Pair<Dock, Dock> {
        val root = root(); val carrier = carrier()
        return (root.level.getBlockEntity(root.plot.centerBlock.offset(rootOffset)) as Dock) to
            (carrier.level.getBlockEntity(carrier.plot.centerBlock.offset(carrierDockOffset)) as Dock)
    }
    private fun handles(a: Dock, b: Dock) = listOfNotNull(handleField.get(a) as? FixedConstraintHandle, handleField.get(b) as? FixedConstraintHandle)

    private fun verifyTransfers(a: Dock, b: Dock) {
        check(a.isLocked && b.isLocked)
        val nodeA = (a.ccWiredElement as dan200.computercraft.api.network.wired.WiredElement).node
        val nodeB = (b.ccWiredElement as dan200.computercraft.api.network.wired.WiredElement).node
        check(!nodeA.connectTo(nodeB)) { "Native ComputerCraft wired edge missing" }
        check(a.battery.receiveEnergy(123, false) == 123 && b.battery.energy == 123) { "FE forward" }
        check(b.battery.receiveEnergy(57, false) == 57 && a.battery.energy == 57) { "FE reverse" }
        val water = CFluidType(Fluids.WATER, DataComponentPatch.EMPTY)
        check(a.tank.insert(water, 250, false) == 250L && b.tank.amount == 250L) { "Fluid forward" }
        check(b.tank.insert(water, 125, false) == 125L && a.tank.amount == 125L) { "Fluid reverse" }
        check(a.getInventory().insertSlot(ItemStack(Items.GOLD_INGOT, 11), 0, false).isEmpty && b.inventory.getItem(0).count == 11) { "Items forward" }
        check(b.getInventory().insertSlot(ItemStack(Items.DIAMOND, 7), 0, false).isEmpty && a.inventory.getItem(0).count == 7) { "Items reverse" }
        checkedTransfers = true
    }

    /** Force the existing post-load CTF bind failure, after the companion already
     * crossed, then require the sole wagon and companion snapshots back in source. */
    private fun rollback() {
        check(case == "wide" && sourceVerified && !targetVerified) { "Prepare and verify the wide fixture, then create its route first" }
        val beforeRoot = root(); val beforeCarrier = carrier()
        val level = TrainDemo.sourceLevel()
        check(beforeRoot.level === level && beforeCarrier.level === level)
        val portal = level.allEntities.filterIsInstance<Portal>().single { !it.isRemoved && it.destDim == TrainPortalTestDestination.level(level).dimension() }
        val link = checkNotNull(SubLevelLinks.linkForSubLevel(beforeRoot.uniqueId))
        val owner = level.getEntity(link.entityId) as CarriageContraptionEntity
        // This fake destination has no Carriage and must fail CTF binding inside
        // IP's native destination-load transaction, just like the base verifier.
        val invalidTarget = CarriageContraptionEntity(owner.type, TrainPortalTestDestination.level(level))
        check(!TrainPortalTransfers.handoff(link, beforeRoot, invalidTarget, portal)) { "Invalid destination unexpectedly accepted" }
        val restoredRoot = root(); val restoredCarrier = carrier()
        check(restoredRoot !== beforeRoot && restoredCarrier !== beforeCarrier) { "The post-load rollback never recreated both transferred bodies" }
        check(restoredRoot.level === level && restoredCarrier.level === level) { "Rollback left a build in the destination" }
        val all = bodies()
        for (id in trainIds + restoredCarrier.uniqueId) check(all.count { it.uniqueId == id } == 1) { "Rollback duplicated body $id" }
        check(TrainDemo.roots().map { it.second.uniqueId }.toSet() == trainIds)
        check(TrainDemo.roots().all { it.second.level === level })
        val cargo = level.getBlockEntity(restoredCarrier.plot.centerBlock.offset(carrierChestOffset)) as ChestBlockEntity
        check(cargo.getItem(0).`is`(Items.EMERALD) && cargo.getItem(0).count == 23)
        val (a, b) = connectors()
        check(a.inventory.getItem(0).`is`(Items.DIAMOND) && a.inventory.getItem(0).count == 7)
        check(b.inventory.getItem(0).`is`(Items.GOLD_INGOT) && b.inventory.getItem(0).count == 11)
        check(a.battery.energy == 57 && b.battery.energy == 123 && a.tank.amount == 125L && b.tank.amount == 250L)
        rollbackVerified = true
        val report = level.server.getWorldPath(LevelResource.ROOT).resolve("train-docking-verification")
        Files.createDirectories(report)
        Files.writeString(report.resolve("rollback.json"), status())
    }

    private fun verify(stage: String) {
        val roots = TrainDemo.roots().map { it.second }
        check(roots.map { it.uniqueId }.toSet() == trainIds && roots.size == 2) { "Wagon UUIDs changed" }
        val carrier = carrier(); val root = root()
        val allBodies = bodies()
        for (id in trainIds + carrier.uniqueId) check(allBodies.count { it.uniqueId == id } == 1) { "Duplicated body $id" }
        val marker = checkNotNull(carrier.userDataTag).getString(MARKER)
        check(marker.isNotEmpty() && allBodies.count { it.userDataTag?.getString(MARKER) == marker } == 1) { "Duplicated carrier cargo snapshot" }
        val cargo = carrier.level.getBlockEntity(carrier.plot.centerBlock.offset(carrierChestOffset)) as ChestBlockEntity
        check(cargo.getItem(0).`is`(Items.EMERALD) && cargo.getItem(0).count == 23) { "Carrier cargo changed" }
        val (a, b) = connectors()
        if (stage == "source") {
            check(root.level === TrainDemo.sourceLevel() && carrier.level === root.level)
            check(a.isLocked && b.isLocked && a.otherConnector === b && b.otherConnector === a)
            check(handles(a, b).single().isValid)
            // Portal width is measured along world Z. The complete wagon fits;
            // the platform's outer edge is outside only in the narrow case.
            roots.forEach { it.forceUpdateGlobalBounds() }; carrier.forceUpdateGlobalBounds()
            check(roots.all { it.boundingBox().minZ() >= -4.5 && it.boundingBox().maxZ() <= 4.5 }) { "The wagons do not fit the narrow aperture: ${roots.map { it.boundingBox() }}" }
            val carrierExtent = maxOf(kotlin.math.abs(carrier.boundingBox().minZ()), kotlin.math.abs(carrier.boundingBox().maxZ()))
            check(carrierExtent > 4.5 && carrierExtent < 15.0) { "Carrier aperture geometry invalid: ${carrier.boundingBox()}" }
            if (!checkedTransfers) verifyTransfers(a, b)
            sourceVerified = true
        } else {
            check(sourceVerified) { "Source was never verified" }
            val target = TrainPortalTestDestination.level(TrainDemo.sourceLevel())
            check(roots.all { it.level === target }) { "Both wagons must arrive" }
            check(initialHandle?.isValid == false) { "Source fixed constraint survived the dimension handoff" }
            check(nativeCarrierTravel > 8.0 && nativeRootTravel > 8.0) { "Native constraint did not carry the platform before the portal: carrier=$nativeCarrierTravel wagon=$nativeRootTravel" }
            if (case == "wide") {
                check(carrier.level === target) { "Fitting docked companion did not arrive" }
                check(a.isLocked && b.isLocked && a.otherConnector === b && b.otherConnector === a) { "Native dock did not reconnect after arrival" }
                check(handles(a, b).single().isValid) { "No real fixed constraint after arrival" }
                val nodeA = (a.ccWiredElement as dan200.computercraft.api.network.wired.WiredElement).node
                val nodeB = (b.ccWiredElement as dan200.computercraft.api.network.wired.WiredElement).node
                check(!nodeA.connectTo(nodeB)) { "Wired transfer did not reconnect" }
                if (!targetVerified) {
                    check(a.battery.receiveEnergy(19, false) == 19 && b.battery.energy == 142) { "FE transfer after arrival" }
                    check(a.tank.insert(CFluidType(Fluids.WATER, DataComponentPatch.EMPTY), 25, false) == 25L && b.tank.amount == 275L) { "Fluid transfer after arrival" }
                    check(a.getInventory().insertSlot(ItemStack(Items.GOLD_INGOT, 2), 0, false).isEmpty && b.inventory.getItem(0).count == 13) { "Item transfer after arrival" }
                }
            } else {
                check(carrier.level === TrainDemo.sourceLevel()) { "Too-wide companion should remain in source" }
                check(!a.isLocked && !b.isLocked && !a.hasOtherConnector() && !b.hasOtherConnector()) { "Too-wide dock did not detach both endpoints" }
                check(handles(a, b).isEmpty()) { "Detached connectors still hold a constraint" }
                check(!a.battery.canReceive() && !b.battery.canReceive()) { "FE link leaked across dimensions" }
                check(a.tank.insert(CFluidType(Fluids.WATER, DataComponentPatch.EMPTY), 1, true) == 0L && b.tank.insert(CFluidType(Fluids.WATER, DataComponentPatch.EMPTY), 1, true) == 0L) { "Fluid link leaked across dimensions" }
                val nodeA = (a.ccWiredElement as dan200.computercraft.api.network.wired.WiredElement).node
                val nodeB = (b.ccWiredElement as dan200.computercraft.api.network.wired.WiredElement).node
                check(!nodeA.disconnectFrom(nodeB)) { "Wired link leaked across dimensions" }
            }
            check(a.inventory.getItem(0).`is`(Items.DIAMOND) && a.inventory.getItem(0).count == 7) { "Train connector cargo changed" }
            check(b.inventory.getItem(0).`is`(Items.GOLD_INGOT) && b.inventory.getItem(0).count == if (case == "wide") 13 else 11) { "Carrier connector cargo changed" }
            check(a.battery.energy == 57 && a.tank.amount == 125L) { "Train connector FE/fluid buffer changed" }
            check(b.battery.energy == if (case == "wide") 142 else 123) { "Carrier connector FE buffer changed" }
            check(b.tank.amount == if (case == "wide") 275L else 250L) { "Carrier connector fluid buffer changed" }
            targetVerified = true
        }
        val report = TrainDemo.sourceLevel().server.getWorldPath(LevelResource.ROOT).resolve("train-docking-verification")
        Files.createDirectories(report)
        Files.writeString(report.resolve("$stage.json"), status())
    }

    private fun bodyStatus(body: ServerSubLevel): Map<String, Any> {
        val center = body.boundingBox().center()
        return mapOf("uuid" to body.uniqueId.toString(), "dimension" to body.level.dimension().location().toString(),
            "center" to listOf(center.x, center.y, center.z),
            "bounds" to listOf(body.boundingBox().minX(), body.boundingBox().minY(), body.boundingBox().minZ(), body.boundingBox().maxX(), body.boundingBox().maxY(), body.boundingBox().maxZ()))
    }

    private fun massStatus(body: ServerSubLevel): Map<String, Any> {
        val tracker = body.massTracker
        val com = tracker.centerOfMass
        val origin = body.plot.centerBlock
        val tensor = tracker.inertiaTensor
        return linkedMapOf("nativeMass" to tracker.mass,
            "localCenterOfMass" to (com?.let { listOf(it.x() - origin.x, it.y() - origin.y, it.z() - origin.z) } ?: emptyList<Double>()),
            "inertiaTensor" to listOf(tensor.m00(), tensor.m01(), tensor.m02(), tensor.m10(), tensor.m11(), tensor.m12(), tensor.m20(), tensor.m21(), tensor.m22()))
    }

    private fun weightStudyStatus(): Map<String, Any> {
        val root = root(); val carrier = carrier()
        val floorMaterials = listOf(Blocks.MAGENTA_WOOL, Blocks.DARK_OAK_PLANKS, Blocks.MAGENTA_CONCRETE, Blocks.IRON_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.TARGET)
            .associate { block -> BuiltInRegistries.BLOCK.getKey(block).toString() to PhysicsBlockPropertyHelper.getMass(carrier.level, carrier.plot.centerBlock, block.defaultBlockState()) }
        return linkedMapOf("enabled" to weightStudy, "profile" to massProfile, "initialBaseCenterSeparation" to initialBaseSeparation,
            "connectionLengthIncludingBasesBlocks" to initialBaseSeparation + 1,
            "expectedEffectiveLength" to if (initialBaseSeparation == 2) "HALF" else "FULL",
            "signalSource" to if (weightStudy) "Native target block power initialized once; no scheduled tick" else "Redstone block",
            "floorGeometry" to "7x7 full cubes: 48 variable deck blocks; fixed signal, posts, lamps, dock and chest",
            "platform" to massStatus(carrier), "wagon" to massStatus(root), "nativeMaterialMasses" to floorMaterials,
            "platformMinimumY" to carrier.boundingBox().minY(), "preparedGeometry" to preparedGeometry)
    }

    private fun status(): String {
        if (case == null) return gson.toJson(mapOf("case" to "none", "ready" to false, "configuredMassProfile" to massProfile, "configuredBaseSeparation" to initialBaseSeparation))
        val (a, b) = connectors()
        fun connectorPosition(dock: Dock, body: ServerSubLevel): List<Double> = body.logicalPose().transformPosition(dock.blockPos.center).let { listOf(it.x, it.y, it.z) }
        return gson.toJson(linkedMapOf("case" to case, "ready" to (a.isLocked && b.isLocked), "portalWidth" to portalWidth,
            "hybridEnabled" to ServerConfig.hybridTrains(), "carriages" to carriageStatus(),
            "weightStudy" to weightStudyStatus(),
            "sagCapture" to mapOf("label" to (captureLabel ?: "none"), "remainingTicks" to captureRemaining, "collectedTicks" to captureSamples.size),
            "sag" to sagMeasurement(a, b, root(), carrier()),
            "sourceVerified" to sourceVerified, "targetVerified" to targetVerified, "nativeTransfersVerified" to checkedTransfers,
            "rollbackVerified" to rollbackVerified,
            "nativeConstraintMotion" to mapOf("platformTravelBeforePortal" to nativeCarrierTravel, "wagonTravelBeforePortal" to nativeRootTravel),
            "wagon" to bodyStatus(root()), "platform" to bodyStatus(carrier()),
            "docking" to mapOf("trainLocked" to a.isLocked, "platformLocked" to b.isLocked,
                "trainHasPeer" to a.hasOtherConnector(), "platformHasPeer" to b.hasOtherConnector(),
                "validConstraints" to handles(a, b).count { it.isValid },
                "trainConnectorPosition" to connectorPosition(a, root()), "platformConnectorPosition" to connectorPosition(b, carrier())),
            "cargo" to mapOf("platformEmeralds" to ((carrier().level.getBlockEntity(carrier().plot.centerBlock.offset(carrierChestOffset)) as ChestBlockEntity).getItem(0).count), "trainConnectorDiamonds" to a.inventory.getItem(0).count,
                "platformConnectorGold" to b.inventory.getItem(0).count, "trainConnectorFE" to a.battery.energy,
                "platformConnectorFE" to b.battery.energy, "trainConnectorFluid" to a.tank.amount, "platformConnectorFluid" to b.tank.amount)))
    }
}
