package de.TeutonStudio.TeutonLib.traintest

import com.railwayteam.railways.content.coupling.TrainUtils
import com.simibubi.create.AllBlocks
import com.simibubi.create.Create
import com.simibubi.create.content.trains.entity.*
import com.simibubi.create.content.trains.graph.*
import com.simibubi.create.content.trains.track.TrackMaterial
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import createtrainfunctionality.AutoConvert
import createtrainfunctionality.LinkRegistry
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.train.CtfPortalAccess
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalGuard
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import qouteall.imm_ptl.core.compat.create_integration.IPCreatePortalEdges
import qouteall.imm_ptl.core.compat.mixin_create.IPCarriageAccessor
import qouteall.imm_ptl.core.portal.Portal
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager
import java.util.UUID
import java.util.Properties
import java.nio.file.Files
import net.minecraft.world.level.storage.LevelResource
import createtrainfunctionality.PlotStorage

/** Drive actual Create carriages through real IP portal edges over real server/physics ticks. */
object TrainTraversalVerification {
    private val log = LoggerFactory.getLogger("TeutonLib/TrainTraversalTest")
    private data class Run(val source: ServerLevel, val destination: ServerLevel, val graph: TrackGraph,
        val train: Train, val carriage: Carriage, val subId: UUID, val sourceX: Double, val targetX: Double,
        val portals: List<Portal>, val origin: BlockPos, val checkpoint: Boolean = false, var ticks: Int = 0, var outbound: Boolean = true,
        var arrivedAt: Int = -1, var returnedAt: Int = -1, val builds: MutableMap<UUID, Int> = mutableMapOf(),
        val visual: Boolean = false, var remainingTravel: Double = 0.0, var rider: Ride? = null,
        val retainPortalCluster: Boolean = false, val portalCluster: List<Portal> = portals)
    private data class Ride(val player: ServerPlayer, val seated: Boolean = false, var seatOffset: BlockPos? = null,
        var phase: Int = 0, var wait: Int = 40, var offset: Vec3? = null,
        var lastDimension: String = "", var dimensionChanges: Int = 0, var earlyCrossing: Boolean = false,
        var bodyDimension: String = "", var seatGraceUntil: Int = 0,
        var rollbackOnly: Boolean = false, var rollbackCompleted: Boolean = false)
    private var run: Run? = null
    private var result = "IDLE"
    private var decoupleCheck: (() -> Boolean)? = null
    private var decoupleTicks = 0

    internal data class HybridFixture(val train: Train, val subId: UUID, val destination: ServerLevel, val portals: List<Portal>)
    internal fun hybridFixture(source: ServerLevel): HybridFixture {
        start(source, false, visual = true)
        val active = checkNotNull(run)
        return HybridFixture(active.train, active.subId, active.destination, active.portals)
    }

    fun start(source: ServerLevel, sameDimension: Boolean, checkpoint: Boolean = false, allBuildable: Boolean = false,
        visual: Boolean = false): String {
        check(run == null && decoupleCheck == null)
        val nativePair = if (!sameDimension && NativeAetherPortalVerification.isRequested())
            NativeAetherPortalVerification.pair(source) else null
        val destination = nativePair?.destination ?: if (sameDimension) source else TrainPortalTestDestination.level(source)
        val x = nativePair?.sourceX ?: if (sameDimension) 240.0 else 80.0
        val target = nativePair?.targetX ?: if (sameDimension) 400.0 else 160.0
        val y = 100.0
        for (level in listOf(source, destination).distinct()) {
            for (cx in 0..30) level.setChunkForced(cx, 0, true)
        }
        val graph = TrackGraph()
        val a = TrackNode(TrackNodeLocation(x - 30.0, y, 0.0).`in`(source), TrackGraph.nextNodeId(), Vec3(0.0, 1.0, 0.0))
        val b = TrackNode(TrackNodeLocation(x - 0.5, y, 0.0).`in`(source), TrackGraph.nextNodeId(), Vec3(0.0, 1.0, 0.0))
        val c = TrackNode(TrackNodeLocation(target + 0.5, y, 0.0).`in`(destination), TrackGraph.nextNodeId(), Vec3(0.0, 1.0, 0.0))
        val d = TrackNode(TrackNodeLocation(target + 30.0, y, 0.0).`in`(destination), TrackGraph.nextNodeId(), Vec3(0.0, 1.0, 0.0))
        listOf(a,b,c,d).forEach(graph::addNode)
        IPCreatePortalEdges.add(b.location, c.location)
        for ((first, second) in listOf(a to b, b to c, c to d)) {
            graph.putConnection(first, second, TrackEdge(first, second, null, TrackMaterial.ANDESITE))
            graph.putConnection(second, first, TrackEdge(second, first, null, TrackMaterial.ANDESITE))
        }
        Create.RAILWAYS.trackNetworks[graph.id] = graph
        val forward = nativePair?.forward ?: portal(source, destination,
            Vec3(x, y + 1.0, 0.0), Vec3(target, y + 1.0, 0.0), false)
        val reverse = nativePair?.reverse ?: portal(destination, source,
            Vec3(target, y + 1.0, 0.0), Vec3(x, y + 1.0, 0.0), true)
        val edge = graph.getConnectionsFrom(a).getValue(b)
        val carriage = carriage(a, b, edge, 18.0)
        val rear = carriage(a, b, edge, 8.0)
        val train = Train(UUID.randomUUID(), UUID.randomUUID(), graph, mutableListOf(carriage, rear), mutableListOf(8), false, 0)
        Create.RAILWAYS.addTrain(train)
        carriage.setContraption(source, contraption(visual))
        spawn(carriage, source)
        rear.setContraption(source, contraption(visual && allBuildable, rearPalette = true))
        spawn(rear, source)
        val entity = checkNotNull(carriage.anyAvailableEntity())
        val linked = checkNotNull(SubLevelLinks.convert(source, entity))
        val chest = source.getBlockEntity(linked.plotOrigin.above()) as ChestBlockEntity
        chest.setItem(0, ItemStack(Items.DIAMOND, 17))
        if (visual) check(source.getBlockEntity(linked.plotOrigin.offset(-10, 3, -1)) is CreativeMotorBlockEntity) {
            "Visual fixture must create a real motor block entity in the front wagon plot"
        }
        // A real null-world call through the transformed method, as Railways uses it.
        carriage.travel(null, graph, -0.1, null, null, 3)
        carriage.travel(null, graph, 0.1, null, null, 3)
        run = Run(source, destination, graph, train, carriage, linked.subLevelId, x, target,
            listOf(forward, reverse), linked.plotOrigin, checkpoint, visual = visual,
            retainPortalCluster = nativePair != null, portalCluster = nativePair?.portals ?: listOf(forward, reverse))
        run!!.builds[linked.subLevelId] = 17
        if (allBuildable) {
            val second = checkNotNull(SubLevelLinks.convert(source, checkNotNull(rear.anyAvailableEntity())))
            (source.getBlockEntity(second.plotOrigin.above()) as ChestBlockEntity).setItem(0, ItemStack(Items.DIAMOND, 19))
            if (visual) check(source.getBlockEntity(second.plotOrigin.offset(-10, 4, -1)) is CreativeMotorBlockEntity) {
                "Visual fixture must create a real motor block entity in the rear wagon plot"
            }
            run!!.builds[second.subLevelId] = 19
        }
        if (visual) source.setBlockAndUpdate(linked.plotOrigin.offset(7, 3, 0), Blocks.MAGENTA_CONCRETE.defaultBlockState())
        run!!.builds.keys.forEach { id ->
            val body = SubLevelContainer.getContainer(source)!!.getSubLevel(id) as ServerSubLevel
            de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.setMode(body,
                de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.Mode.DRIVING)
        }
        // This graph/train was created after login. Replay Create's native railway
        // snapshot so the real client can bind carriage entities and draw couplings.
        if (visual) source.server.playerList.players.forEach(Create.RAILWAYS::playerLogin)
        result = if (visual) "READY visual ships=${run!!.builds.keys.joinToString(",")} " +
            "destination=${destination.dimension().location()} portalKind=${if (nativePair != null) "native-aether" else "generic-ip"}" else "RUNNING"
        return result
    }

    private fun portal(source: ServerLevel, destination: ServerLevel, from: Vec3, to: Vec3, reverse: Boolean): Portal {
        val portal = Portal(Portal.ENTITY_TYPE, source)
        portal.setOriginPos(from)
        portal.setDestinationDimension(destination.dimension())
        portal.setDestination(to)
        portal.setOrientationAndSize(Vec3(0.0, 0.0, if (reverse) -1.0 else 1.0), Vec3(0.0, 1.0, 0.0), 12.0, 12.0)
        source.addFreshEntity(portal)
        return portal
    }

    private fun carriage(a: TrackNode, b: TrackNode, edge: TrackEdge, position: Double): Carriage {
        val front = TravellingPoint(a, b, edge, position, false)
        val back = TravellingPoint(a, b, edge, position - 2.0, false)
        return Carriage(CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, CompoundTag(), front, back), null, 0)
    }

    private fun spawn(carriage: Carriage, level: ServerLevel) {
        carriage.manageEntities(level)
        if (carriage.anyAvailableEntity() == null) {
            // Newly forced chunks become ticking on the following tick. Use Create's own
            // spawn routine here so the command fixture does not depend on that scheduling.
            val record = carriage.getDimensional(level)
            record.javaClass.getDeclaredMethod("createEntity", Level::class.java, Boolean::class.javaPrimitiveType)
                .also { it.isAccessible = true }.invoke(record, level, true)
        }
        check(carriage.anyAvailableEntity() != null) { "Create did not spawn the fixture carriage" }
    }

    private fun contraption(visual: Boolean = false, rearPalette: Boolean = false): CarriageContraption =
        de.TeutonStudio.TeutonLib.integration.hybrid.HybridCarriageContraption(Direction.EAST).also {
        it.anchor = BlockPos.ZERO
        it.bounds = AABB(-1.0, 0.0, -1.0, 2.0, 3.0, 2.0)
        it.blocks[BlockPos.ZERO] = StructureBlockInfo(BlockPos.ZERO, AllBlocks.SMALL_BOGEY.defaultState, null)
        it.blocks[BlockPos.ZERO.above()] = StructureBlockInfo(BlockPos.ZERO.above(), Blocks.CHEST.defaultBlockState(), null)
        if (visual) {
            it.bounds = AABB(-12.0, 0.0, -2.0, 13.0, 5.0, 2.0)
            if (rearPalette) {
                val support = BlockPos(0, 2, 0)
                it.blocks[support] = StructureBlockInfo(support, Blocks.ORANGE_CONCRETE.defaultBlockState(), null)
            }
            for (x in -12..12) for (z in -1..1) {
                val position = BlockPos(x, if (rearPalette) 3 else 2, z)
                val block = if (rearPalette) {
                    if (x < 0) Blocks.ORANGE_CONCRETE else Blocks.LIME_CONCRETE
                } else {
                    if (x < 0) Blocks.YELLOW_CONCRETE else Blocks.CYAN_CONCRETE
                }
                it.blocks[position] = StructureBlockInfo(position, block.defaultBlockState(), null)
            }
            // Real kinetic block entities exercise Flywheel's embedded instance
            // lighting on both halves, rather than only the static wagon mesh.
            // Keep the seat fixture's x=+/-8,z=0 positions available.
            val kineticY = if (rearPalette) 4 else 3
            for (motorX in listOf(-10, 8)) {
                val motor = BlockPos(motorX, kineticY, -1)
                val shaft = motor.east()
                val cog = shaft.east()
                it.blocks[motor] = StructureBlockInfo(motor, AllBlocks.CREATIVE_MOTOR.defaultState
                    .setValue(BlockStateProperties.FACING, Direction.EAST), null)
                it.blocks[shaft] = StructureBlockInfo(shaft, AllBlocks.SHAFT.defaultState
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.X), null)
                it.blocks[cog] = StructureBlockInfo(cog, AllBlocks.COGWHEEL.defaultState
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.X), null)
            }
        }
        it.storage.initialize()
    }

    fun tick(server: MinecraftServer) {
        decoupleCheck?.let { verify ->
            decoupleTicks++
            try {
                if (verify()) decoupleCheck = null
            } catch (failure: Throwable) {
                decoupleCheck = null
                result = "FAIL decoupling: $failure"
                log.error("Decoupling verification FAIL", failure)
            }
            return
        }
        val active = run ?: return
        try {
            active.ticks++
            check(active.ticks < if (active.visual) 12000 else 260) { "Traversal timed out; phase=$result; wheel=${active.carriage.leadingPoint.node1.location}" }
            val home = server.allLevels.firstOrNull {
                SubLevelContainer.getContainer(it)?.getSubLevel(active.subId) != null
            } ?: error("Build disappeared")
            val sub = SubLevelContainer.getContainer(home)!!.getSubLevel(active.subId) as ServerSubLevel
            if (RigidBodyHandle.of(sub)?.isValid != true) return
            val delta = if (active.visual) active.remainingTravel.coerceIn(-0.4, 0.4) else if (active.outbound) 0.4 else -0.4
            val carriages = if (delta >= 0.0) active.train.carriages else active.train.carriages.reversed()
            if (delta != 0.0) for (carriage in carriages) carriage.travel(active.source, active.graph, delta, null, null, 3)
            if (active.visual) active.remainingTravel -= delta
            for (level in server.allLevels) {
                AutoConvert.tick(level)
                SubLevelLinks.syncAll(level)
            }
            check(server.allLevels.sumOf { level ->
                SubLevelContainer.getContainer(level)?.allSubLevels?.count { it.uniqueId == active.subId } ?: 0
            } == 1) { "Build duplicated" }
            val link = checkNotNull(SubLevelLinks.linkForSubLevel(active.subId))
            val owner = checkNotNull(CtfPortalAccess.entity(link))
            val chest = owner.level().getBlockEntity(link.plotOrigin.above()) as ChestBlockEntity
            check(chest.getItem(0).`is`(Items.DIAMOND) && chest.getItem(0).count == 17) { "Cargo changed" }
            for ((id, count) in active.builds) {
                check(server.allLevels.sumOf { level ->
                    SubLevelContainer.getContainer(level)?.allSubLevels?.count { it.uniqueId == id } ?: 0
                } == 1)
                val member = checkNotNull(SubLevelLinks.linkForSubLevel(id))
                val memberLevel = checkNotNull(server.getLevel(member.dimension))
                val cargo = (memberLevel.getBlockEntity(member.plotOrigin.above()) as ChestBlockEntity).getItem(0)
                check(cargo.`is`(Items.DIAMOND) && cargo.count == count)
                if (active.visual) {
                    val front = id == active.subId
                    val roofY = if (front) 2 else 3
                    val left = if (front) Blocks.YELLOW_CONCRETE else Blocks.ORANGE_CONCRETE
                    val right = if (front) Blocks.CYAN_CONCRETE else Blocks.LIME_CONCRETE
                    check(memberLevel.getBlockState(member.plotOrigin.offset(-12, roofY, 0)).`is`(left) &&
                        memberLevel.getBlockState(member.plotOrigin.offset(12, roofY, 0)).`is`(right)) {
                        "Visual wagon $id lost its connected roof to another body"
                    }
                    val detail = if (front) member.plotOrigin.offset(7, 3, 0) else member.plotOrigin.offset(0, 2, 0)
                    check(memberLevel.getBlockState(detail).`is`(if (front) Blocks.MAGENTA_CONCRETE else Blocks.ORANGE_CONCRETE)) {
                        "Visual wagon $id lost its ${if (front) "live edit" else "roof support"}"
                    }
                }
            }
            // Check every tick around the rails so drops are caught before falling away.
            // Generated Nether vegetation can break independently far below this corridor.
            val drops = listOf(active.source, active.destination).distinct().flatMap { level ->
                val centres = buildList {
                    if (level === active.source) add(active.sourceX)
                    if (level === active.destination) add(active.targetX)
                }
                level.getEntitiesOfClass(ItemEntity::class.java,
                    AABB(centres.min() - 45.0, 94.0, -10.0, centres.max() + 45.0, 110.0, 10.0))
            }
            check(drops.isEmpty()) { "Train blocks were dropped: ${drops.map { it.item.toString() + " at " + it.position() }}" }
            if (active.visual) {
                if (active.rider != null) {
                    riderTick(active)
                    return
                }
                result = (if (kotlin.math.abs(active.remainingTravel) < 1e-7) "READY" else "RUNNING") +
                    " visual ship=${active.subId} dimension=${link.dimension.location()} position=${owner.position()}"
                return
            }
            val leading = active.carriage.leadingPoint.getPosition(active.graph)
            if (active.outbound && owner.level() === active.destination && leading.x > active.targetX + 16.0) {
                if (active.checkpoint) {
                    val properties = Properties().also {
                        it["train"] = active.train.id.toString()
                        it["sublevel"] = active.subId.toString()
                        it["dimension"] = active.destination.dimension().location().toString()
                    }
                    Files.newBufferedWriter(server.getWorldPath(LevelResource.ROOT).resolve("train-portal-checkpoint.properties")).use {
                        properties.store(it, "Disposable train traversal verification")
                    }
                    result = "PASS checkpoint: train parked beyond portal with its one build and 17 diamonds"
                    run = null
                    return
                }
                active.arrivedAt = active.ticks
                active.outbound = false
                log.info("Train traversal outbound PASS at tick {}", active.ticks)
            } else if (!active.outbound && owner.level() === active.source && leading.x < active.sourceX - 10.0) {
                active.returnedAt = active.ticks
                // Force a real post-load bind failure and verify the sole build returns with cargo.
                val currentSub = SubLevelContainer.getContainer(active.source)!!.getSubLevel(active.subId) as ServerSubLevel
                val invalidTarget = CarriageContraptionEntity(owner.type, active.destination)
                check(!TrainPortalTransfers.handoff(link, currentSub, invalidTarget, active.portals.first()))
                val restored = checkNotNull(SubLevelLinks.linkForSubLevel(active.subId))
                check(restored.dimension == active.source.dimension())
                val restoredChest = active.source.getBlockEntity(restored.plotOrigin.above()) as ChestBlockEntity
                check(restoredChest.getItem(0).`is`(Items.DIAMOND) && restoredChest.getItem(0).count == 17)
                // Disassembly must see the live plot, including edits and cargo.
                SubLevelLinks.beforeDisassembly(owner)
                check(owner.contraption.blocks.containsKey(BlockPos.ZERO.above()))
                check(LinkRegistry.lookup(active.source, owner.uuid, active.train.id, 0) == null ||
                    LinkRegistry.claimedBy(active.source, active.subId)?.entityId() == owner.uuid)
                result = "PASS ${if (active.source === active.destination) "same-dimension" else "cross-dimension"} " +
                    "outbound+return ticks=${active.ticks}; ${active.builds.size} unique build(s); preserved cargo; " +
                    "mixed/plain carriage; null-world travel; rollback; live disassembly"
                log.info("Train traversal {}", result)
                cleanup(active)
                run = null
            }
        } catch (failure: Throwable) {
            result = "FAIL $failure"
            log.error("Train traversal FAIL", failure)
            cleanup(active)
            run = null
        }
    }

    fun status() = result

    /** Real networked player: ordinary roof collision and motion, including early player crossing. */
    fun startRider(source: ServerLevel, player: ServerPlayer, seated: Boolean = false): String {
        start(source, false, allBuildable = true, visual = true)
        val active = checkNotNull(run)
        active.rider = Ride(player, seated)
        val link = checkNotNull(SubLevelLinks.linkForSubLevel(active.subId))
        val sub = SubLevelContainer.getContainer(source)!!.getSubLevel(active.subId) as ServerSubLevel
        val seatOffset = listOf(-8, 8).map { BlockPos(it, 3, 0) }.maxBy { offset ->
            sub.logicalPose().transformPosition(Vec3.atCenterOf(link.plotOrigin.offset(offset))).x
        }
        val feet = sub.logicalPose().transformPosition(
            Vec3.atLowerCornerOf(link.plotOrigin.offset(seatOffset)).add(.5, .05, .5))
        player.stopRiding()
        player.setGameMode(GameType.SURVIVAL)
        player.abilities.flying = false
        player.onUpdateAbilities()
        ServerTeleportationManager.of(source.server).forceTeleportPlayer(player, source.dimension(), feet)
        player.deltaMovement = Vec3.ZERO
        if (seated) {
            active.rider!!.seatOffset = seatOffset
            val seatPlot = link.plotOrigin.offset(seatOffset)
            de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.setMode(sub,
                de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.Mode.EDITING)
            source.setBlockAndUpdate(seatPlot, AllBlocks.SEATS.get(net.minecraft.world.item.DyeColor.RED).defaultState)
            de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.setMode(sub,
                de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.Mode.DRIVING)
            // The seat entity stays in the plot. Sable maps its player's riding position into the world.
            SeatBlock.sitDown(source, seatPlot, player)
            check(player.vehicle is SeatEntity) { "Player did not mount the live Create seat" }
        }
        result = "RUNNING real player ${if (seated) "seat" else "roof"} contact"
        return result
    }

    private fun riderTick(active: Run) {
        val ride = checkNotNull(active.rider)
        val player = ride.player
        check(player.isAlive && !player.isRemoved) { "Rider disappeared" }
        val link = checkNotNull(SubLevelLinks.linkForSubLevel(active.subId))
        val level = checkNotNull(active.source.server.getLevel(link.dimension))
        val sub = SubLevelContainer.getContainer(level)!!.getSubLevel(active.subId) as ServerSubLevel
        val support = (player as EntityMovementExtension).`sable$getTrackingSubLevel`()
        if (ride.phase == 0) {
            if (--ride.wait > 0) return
            if (ride.seated) check(validSeat(ride, link, level)) { "Real player did not establish plot-seat contact" }
            else check(support === sub && player.onGround()) { "Real player did not establish roof contact: support=$support, pos=${player.position()}" }
            if (ride.rollbackOnly) {
                check(player.level() === active.source) { "Seat rollback changed the player's dimension" }
                if (!ride.rollbackCompleted) {
                    val owner = checkNotNull(CtfPortalAccess.entity(link))
                    val invalidTarget = CarriageContraptionEntity(owner.type, active.destination)
                    check(!TrainPortalTransfers.handoff(link, sub, invalidTarget, active.portals.first())) {
                        "Invalid target unexpectedly accepted a seated train transfer"
                    }
                    ride.rollbackCompleted = true
                    ride.wait = 40
                    result = "RUNNING real player seated rollback remount"
                } else {
                    result = "PASS real player seated rollback; source dimension and live plot seat retained after forced bind failure"
                    cleanup(active)
                    run = null
                }
                return
            }
            ride.offset = sub.logicalPose().transformPositionInverse(player.position())
                .subtract(Vec3.atLowerCornerOf(sub.plot.centerBlock))
            ride.lastDimension = player.level().dimension().location().toString()
            ride.bodyDimension = level.dimension().location().toString()
            active.remainingTravel = 40.0
            ride.phase = 1
        } else {
            val bodyDimension = level.dimension().location().toString()
            if (ride.seated && ride.bodyDimension != bodyDimension) {
                ride.bodyDimension = bodyDimension
                // IP's ordinary seat transfer deliberately lands a rider on the deck
                // while its delayed real-seat retries wait for plot chunks to load.
                ride.seatGraceUntil = active.ticks + 60
            }
            val dimension = player.level().dimension().location().toString()
            if (ride.lastDimension != dimension) { ride.lastDimension = dimension; ride.dimensionChanges++ }
            check(ride.dimensionChanges <= 2) { "Player bounced between dimensions ${ride.dimensionChanges} times" }
            if (ride.phase == 1 && level === active.source && player.level() === active.destination)
                ride.earlyCrossing = true
            var expected = sub.logicalPose().transformPosition(Vec3.atLowerCornerOf(sub.plot.centerBlock).add(ride.offset!!))
            if (player.level() !== level) {
                val face = active.portals.single { it.level() === level }
                check(player.level().dimension() == face.destDim) { "Rider entered unrelated dimension" }
                expected = face.transformPoint(expected)
            }
            val awaitingNativeSeat = ride.seated && active.ticks <= ride.seatGraceUntil &&
                pendingSeat(ride, link, level)
            if (!awaitingNativeSeat) {
                // Packets follow the client's interpolated pose while the train moves.
                val settled = (ride.phase == 2 || ride.phase == 4) && ride.wait <= 20
                val distanceLimit = if (settled) 4.0 else 36.0
                check(player.position().distanceToSqr(expected) < distanceLimit) {
                    "Real player lost wagon: seated=${ride.seated}, phase=${ride.phase}, player=${player.position()}, expected=$expected, support=$support, vehicle=${player.vehicle}"
                }
            }
            if (kotlin.math.abs(active.remainingTravel) < 1e-7) {
                if (ride.phase == 1 || ride.phase == 3) { ride.phase++; ride.wait = if (ride.seated) 80 else 40 }
                // Native IP re-creates player seats after plot loading, with delayed retries.
                // Require a real mount for the final 20 ticks, rather than accepting its queue.
                if (!ride.seated || ride.wait <= 20) {
                    check(player.level() === level && if (ride.seated) validSeat(ride, link, level)
                        else support === sub && !support.isRemoved) {
                        "Rider retained wrong body after handoff: phase=${ride.phase}, support=$support, owner=$sub, vehicle=${player.vehicle}"
                    }
                }
                if (--ride.wait <= 0) {
                    if (ride.phase == 2) { ride.phase = 3; active.remainingTravel = -40.0 }
                    else {
                        check((ride.seated || ride.earlyCrossing) && ride.dimensionChanges == 2) {
                            "Early player crossing not exercised or dimension changes unexpected: early=${ride.earlyCrossing}, changes=${ride.dimensionChanges}"
                        }
                        result = if (ride.seated)
                            "PASS real player seated outbound+return; live plot seat restored; exactly two dimension changes; mount and relative position retained for 20 ticks after each crossing"
                        else "PASS real player standing outbound+return; early player crossing; exactly two dimension changes; current body support and relative position retained for 20 ticks after each crossing"
                        cleanup(active)
                        run = null
                        return
                    }
                }
            }
        }
        result = "RUNNING real player phase=${ride.phase} remaining=${active.remainingTravel}"
    }

    private fun validSeat(ride: Ride, link: SubLevelLinks.Link, level: ServerLevel): Boolean {
        val seat = ride.player.vehicle as? SeatEntity ?: return false
        val expected = link.plotOrigin.offset(checkNotNull(ride.seatOffset))
        return seat.isAlive && seat.level() === level && seat.blockPosition() == expected &&
            level.getBlockState(expected).block is SeatBlock
    }

    /** Separate probe so the successful round-trip fixture keeps its original sequence. */
    fun startSeatedRollback(source: ServerLevel, player: ServerPlayer): String {
        startRider(source, player, seated = true)
        checkNotNull(run?.rider).rollbackOnly = true
        result = "RUNNING real player seated rollback contact"
        return result
    }

    /** A delayed native seat is a bounded transition allowance, never a successful seat assertion. */
    private fun pendingSeat(ride: Ride, link: SubLevelLinks.Link, level: ServerLevel): Boolean {
        val type = Class.forName("qouteall.imm_ptl.core.compat.sable_integration.IPShipRiderCarry")
        val pending = type.getDeclaredField("PENDING_SITS").also { it.isAccessible = true }.get(null) as List<*>
        val expectedSeat = link.plotOrigin.offset(checkNotNull(ride.seatOffset))
        return pending.any { entry ->
            entry != null && listOf("rider" to ride.player.uuid, "dim" to level.dimension(), "seatPos" to expectedSeat).all { (name, expected) ->
                entry.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(entry) == expected
            }
        }
    }

    fun visualStep(distance: Double): String {
        val active = checkNotNull(run)
        check(active.visual && kotlin.math.abs(active.remainingTravel) < 1e-7 && distance.isFinite() && kotlin.math.abs(distance) <= 40.0)
        active.remainingTravel = distance
        result = "RUNNING visual ship=${active.subId}"
        return result
    }

    fun endVisual(): String {
        run?.let { check(it.visual); cleanup(it) }
        run = null
        result = "PASS visual fixture cleaned"
        return result
    }

    fun loaded(server: MinecraftServer): String {
        val path = server.getWorldPath(LevelResource.ROOT).resolve("train-portal-checkpoint.properties")
        val values = Properties().also { properties -> Files.newBufferedReader(path).use(properties::load) }
        val train = Create.RAILWAYS.trains[UUID.fromString(values.getProperty("train"))] ?: return "RUNNING train loading"
        checkNotNull(train.graph) { "Saved train lost its track graph" }
        check(train.carriages.all { it.leadingPoint.edge != null && it.trailingPoint.edge != null }) {
            "Saved carriage lost its travelling points"
        }
        val id = UUID.fromString(values.getProperty("sublevel"))
        for (carriage in train.carriages) {
            for ((key, record) in (carriage as IPCarriageAccessor).ip_getEntities()) {
                val level = server.getLevel(qouteall.imm_ptl.core.compat.create_integration.IPCreateTrainSides.realDimension(key)) ?: continue
                val anchor = record.positionAnchor ?: continue
                val center = net.minecraft.world.level.ChunkPos(BlockPos.containing(anchor))
                for (dx in -1..1) for (dz in -1..1) level.setChunkForced(center.x + dx, center.z + dz, true)
                level.getChunk(center.x, center.z)
                carriage.manageEntities(level)
            }
        }
        val link = SubLevelLinks.linkForSubLevel(id) ?: return "RUNNING CTF relinking: " + train.carriages.map { carriage ->
            (carriage as IPCarriageAccessor).ip_getEntities().map { (key, record) ->
                "${key.location()}:anchor=${record.positionAnchor},entity=${record.entity.get()?.id}"
            }
        }
        val owner = CtfPortalAccess.entity(link) ?: return "RUNNING carriage loading"
        check(owner.level().dimension().location().toString() == values.getProperty("dimension"))
        check(CtfPortalAccess.carriage(link) === train.carriages.first())
        check(server.allLevels.sumOf { level -> CtfPortalAccess.entries(level).count { it.subLevelId() == id } } == 1)
        val chest = owner.level().getBlockEntity(link.plotOrigin.above()) as ChestBlockEntity
        check(chest.getItem(0).`is`(Items.DIAMOND) && chest.getItem(0).count == 17)
        val bridge = checkNotNull(PlotStorage.bridgeOf(train.carriages.first().storage))
        check(!bridge.sealed() && bridge.origin() == link.plotOrigin && bridge.level() === owner.level())
        return "PASS restart: destination carriage, one saved ownership entry, 17 diamonds, live storage bridge"
    }

    private fun cleanup(active: Run) {
        active.rider?.player?.let { player ->
            val seat = player.vehicle as? SeatEntity
            player.stopRiding()
            seat?.discard()
            (player as EntityMovementExtension).`sable$setTrackingSubLevel`(null)
            player.setGameMode(GameType.SPECTATOR)
            ServerTeleportationManager.of(active.source.server).forceTeleportPlayer(player,
                active.source.dimension(), Vec3(72.0, 107.0, 9.0))
        }
        for (id in active.builds.keys) SubLevelLinks.linkForSubLevel(id)?.let { link ->
            CtfPortalAccess.detachLink(link)
            val level = active.source.server.getLevel(link.dimension)!!
            CtfPortalAccess.forgetSource(level, link.subLevelId)
            val container = SubLevelContainer.getContainer(level)!!
            container.getSubLevel(link.subLevelId)?.let {
                container.removeSubLevel(it, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED)
            }
            CtfPortalAccess.clearChunkCache(level)
        }
        Create.RAILWAYS.removeTrain(active.train.id)
        Create.RAILWAYS.trackNetworks.remove(active.graph.id)
        // Native Aether's four-way cluster is prepared once for the complete
        // visual/standing/seated/rollback suite and retains its real track links.
        if (!active.retainPortalCluster) active.portalCluster.forEach { it.discard() }
        active.carriage.forEachPresentEntity { it.discard() }
        active.train.carriages.forEach { carriage -> carriage.forEachPresentEntity { it.discard() } }
    }

    /** Exercise Steam 'n' Rails' real splitTrain, including both Carriage.travel(null, ...) calls. */
    fun decouple(level: ServerLevel): String {
        check(run == null && decoupleCheck == null)
        val graph = TrackGraph()
        val a = TrackNode(TrackNodeLocation(600.0, 100.0, 0.0).`in`(level), TrackGraph.nextNodeId(), Vec3(0.0, 1.0, 0.0))
        val b = TrackNode(TrackNodeLocation(700.0, 100.0, 0.0).`in`(level), TrackGraph.nextNodeId(), Vec3(0.0, 1.0, 0.0))
        graph.addNode(a); graph.addNode(b)
        val edge = TrackEdge(a,b,null,TrackMaterial.ANDESITE)
        graph.putConnection(a,b,edge)
        graph.putConnection(b,a,TrackEdge(b,a,null,TrackMaterial.ANDESITE))
        Create.RAILWAYS.trackNetworks[graph.id] = graph
        for (cx in 36..45) for (cz in -1..0) {
            level.setChunkForced(cx, cz, true)
            level.getChunk(cx, cz)
        }
        val carriages = mutableListOf(carriage(a,b,edge,40.0), carriage(a,b,edge,30.0))
        val train = Train(UUID.randomUUID(), UUID.randomUUID(), graph, carriages, mutableListOf(8), false, 0)
        Create.RAILWAYS.addTrain(train)
        var split: Train? = null
        val builds = mutableListOf<SubLevelLinks.Link>()
        fun cleanup() {
            for (build in builds) {
                CtfPortalAccess.detachLink(build)
                CtfPortalAccess.forgetSource(level, build.subLevelId)
                val container = SubLevelContainer.getContainer(level)!!
                container.getSubLevel(build.subLevelId)?.let {
                    container.removeSubLevel(it, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED)
                }
            }
            CtfPortalAccess.clearChunkCache(level)
            (train.carriages + split?.carriages.orEmpty()).forEach { it.forEachPresentEntity { entity -> entity.discard() } }
            Create.RAILWAYS.removeTrain(train.id)
            split?.let { Create.RAILWAYS.removeTrain(it.id) }
            Create.RAILWAYS.trackNetworks.remove(graph.id)
        }
        try {
            carriages.forEach { it.setContraption(level, contraption()) }
            // Check the exact readiness predicate Create uses instead of assuming
            // that a fixed number of ticks completed all asynchronous chunk work.
            decoupleTicks = 0
            decoupleCheck = {
                var complete = false
                try {
                    val entities = carriages.toList().map { carriage ->
                        carriage.manageEntities(level)
                        val anchor = carriage.getDimensional(level).positionAnchor
                        if (anchor == null || !CarriageEntityHandler.isActiveChunk(level, BlockPos.containing(anchor))) null
                        else {
                            // Reuse Create's own createEntity routine if manageEntities
                            // has not restored the serialized fixture carriage yet.
                            spawn(carriage, level)
                            carriage.anyAvailableEntity()?.takeIf {
                                it.isAlive && level.getEntity(it.uuid) === it &&
                                    CarriageEntityHandler.isActiveChunk(level, it.blockPosition())
                            }
                        }
                    }
                    if (entities.any { it == null }) {
                        val waiting = carriages.map { carriage ->
                            val anchor = carriage.getDimensional(level).positionAnchor
                            "carriage=${carriage.id},anchor=$anchor,active=${anchor?.let {
                                CarriageEntityHandler.isActiveChunk(level, BlockPos.containing(it)) }},entity=${carriage.anyAvailableEntity()?.uuid}"
                        }
                        check(decoupleTicks < 200) { "Decoupling entity-ticking readiness timed out: $waiting" }
                        result = "RUNNING decoupling waiting for entity-ticking chunks: $waiting"
                        false
                    } else {
                        complete = true
                        entities.forEachIndexed { index, entity ->
                            val build = checkNotNull(SubLevelLinks.convert(level, checkNotNull(entity)))
                            builds.add(build)
                            (level.getBlockEntity(build.plotOrigin.above()) as ChestBlockEntity)
                                .setItem(0, ItemStack(Items.DIAMOND, 17 + index))
                            de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.setMode(
                                SubLevelContainer.getContainer(level)!!.getSubLevel(build.subLevelId) as ServerSubLevel,
                                de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.Mode.DRIVING)
                        }
                        val delegate = TravellingPoint.IPortalListener { false }
                        check(TrainPortalGuard.portalListener(null, carriages.first(), delegate) === delegate)
                        split = TrainUtils.splitTrain(train, 1)
                        check(split !== train && train.carriages.size == 1 && split!!.carriages.size == 1)
                        check(split!!.carriages.first().train === split)
                        SubLevelLinks.syncAll(level)
                        builds.forEachIndexed { index, build ->
                            val owner = checkNotNull(CtfPortalAccess.carriage(build))
                            val saved = CtfPortalAccess.entries(level).single { it.subLevelId() == build.subLevelId }
                            check(saved.trainId() == owner.train.id && saved.carriageIndex() == 0) {
                                "CTF split identity: saved=${saved.trainId()}/${saved.carriageIndex()}, " +
                                    "expected=${owner.train.id}/0, entity=${level.getEntity(build.entityId)}, " +
                                    "owner=${CtfPortalAccess.entity(build)}, carriage=${(CtfPortalAccess.entity(build) as? CarriageContraptionEntity)?.carriage}"
                            }
                            val cargo = (level.getBlockEntity(build.plotOrigin.above()) as ChestBlockEntity).getItem(0)
                            check(cargo.`is`(Items.DIAMOND) && cargo.count == 17 + index)
                        }
                        result = "PASS real Railways splitTrain; 2 CTF trains with 1 carriage; saved owners+cargo retained; null-world listener unchanged"
                        true
                    }
                } catch (failure: Throwable) {
                    complete = true
                    throw failure
                } finally { if (complete) cleanup() }
            }
            result = "RUNNING decoupling"
            return result
        } catch (failure: Throwable) {
            cleanup()
            throw failure
        }
    }
}
