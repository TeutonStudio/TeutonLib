package de.TeutonStudio.TeutonLib.traintest

import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity
import com.happysg.radar.registry.ModBlocks
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.simibubi.create.AllBlocks
import com.simibubi.create.Create
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.contraptions.Contraption
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import com.simibubi.create.content.trains.entity.Train
import com.simibubi.create.content.trains.entity.CarriageContraption
import com.simibubi.create.content.trains.graph.*
import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.track.*
import createtrainfunctionality.AutoConvert
import createtrainfunctionality.SubLevelLinks
import createtrainfunctionality.TrainConfig
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import de.TeutonStudio.TeutonLib.integration.hose.*
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity
import dev.simulated_team.simulated.index.SimBlocks
import net.createmod.catnip.animation.LerpedFloat
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import qouteall.imm_ptl.core.compat.create_integration.IPCreatePortalEdges
import qouteall.imm_ptl.core.portal.Portal
import java.util.UUID

/** Opt-in film set. Every shot uses the same native station-built train and real machines. */
object TrainDemo {
    private lateinit var source: ServerLevel
    private lateinit var station: StationBlockEntity
    private var train: Train? = null
    private val start = BlockPos(44, 100, 0)
    private var travel = 0.0
    private var direction = 1.0
    private var failure: String? = null
    private var checkedBodies: Set<UUID>? = null
    private var nativeBaseline = false
    private val nativeAdopted = mutableSetOf<UUID>()
    private val angles = mutableMapOf<String, Double>()
    private val pending = ArrayDeque<() -> Unit>()
    private val mechanical = BlockPos(2, 3, 0)
    private val swivel = BlockPos(6, 3, 0)
    private val radar = BlockPos(2, 3, 0)
    private val hose = BlockPos(7, 4, 3)

    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        val root = Commands.literal("teutonlib_train_demo").requires { it.hasPermission(4) }
        fun action(name: String, run: () -> Unit) { root.then(Commands.literal(name).executes { ctx ->
            try { run(); ctx.source.sendSuccess({ Component.literal(status()) }, false) }
            catch (e: Throwable) { failure = e.stackTraceToString(); ctx.source.sendFailure(Component.literal("FAIL $e")) }
            1
        }) }
        root.then(Commands.literal("prepare").executes { ctx -> prepare(ctx.source.level); ctx.source.sendSuccess({ Component.literal(status()) }, false); 1 })
        action("enter") { check(station.enterAssemblyMode(null)); check(StationBuildRuntime.bodies(station).size == 1) }
        root.then(Commands.literal("bogey").then(Commands.argument("offset", IntegerArgumentType.integer()).executes { ctx ->
            val player = FakePlayerFactory.getMinecraft(source)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AllBlocks.RAILWAY_CASING.asItem(), 16))
            val pos = start.east(IntegerArgumentType.getInteger(ctx, "offset"))
            check(station.trackClicked(player, InteractionHand.MAIN_HAND, AllBlocks.TRACK.get(), source.getBlockState(pos), pos))
            ctx.source.sendSuccess({ Component.literal(status()) }, false); 1
        }))
        root.then(Commands.literal("deck").then(Commands.argument("wagon", IntegerArgumentType.integer(0, 1)).executes { ctx ->
            deck(IntegerArgumentType.getInteger(ctx, "wagon")); ctx.source.sendSuccess({ Component.literal(status()) }, false); 1
        }))
        action("machines") { machines() }
        action("run") { startMachines() }
        action("assemble") { assemble() }
        action("assemble-native") { assemble(native = true) }
        action("route") { route() }
        action("verify") { verify() }
        action("status") {}
        root.then(Commands.literal("hose").then(Commands.argument("speed", IntegerArgumentType.integer(-64, 64)).executes { ctx ->
            val pulley = if (train == null) source.getBlockEntity(body(1).plot.centerBlock.offset(hose)) as HosePulleyBlockEntity
                else roots().flatMap { (_, sub) -> sub.plot.loadedChunks.flatMap { it.chunk.blockEntities.values } }.filterIsInstance<HosePulleyBlockEntity>().single()
            val motor = pulley.level!!.getBlockEntity(pulley.blockPos.west()) as CreativeMotorBlockEntity
            motor.generatedSpeed.setValue(IntegerArgumentType.getInteger(ctx, "speed"))
            ctx.source.sendSuccess({ Component.literal("hose offset=${pulley.getInterpolatedOffset(1f)} speed=${pulley.speed}") }, false); 1
        }))
        root.then(Commands.literal("move").then(Commands.argument("distance", DoubleArgumentType.doubleArg(-120.0, 120.0)).executes { ctx ->
            travel += DoubleArgumentType.getDouble(ctx, "distance")
            ctx.source.sendSuccess({ Component.literal(status()) }, false); 1
        }))
        TrainDockingDemo.commands(root)
        event.dispatcher.register(root)
    }

    private fun prepare(level: ServerLevel) {
        source = level
        for (x in 1..7) for (z in -2..2) level.setChunkForced(x, z, true)
        for (x in 24..108) {
            level.setBlockAndUpdate(BlockPos(x, 99, 0), Blocks.POLISHED_ANDESITE.defaultBlockState())
            level.setBlockAndUpdate(BlockPos(x, 100, 0), AllBlocks.TRACK.defaultState.setValue(TrackBlock.SHAPE, TrackShape.XO))
        }
        val pos = start.north(4)
        level.setBlockAndUpdate(pos, AllBlocks.TRACK_STATION.defaultState)
        station = level.getBlockEntity(pos) as StationBlockEntity
        TrackTargetingBehaviour::class.java.getDeclaredField("targetTrack").also { it.isAccessible = true }.set(station.edgePoint, start.subtract(pos))
        TrackTargetingBehaviour::class.java.getDeclaredField("targetDirection").also { it.isAccessible = true }.set(station.edgePoint, Direction.AxisDirection.POSITIVE)
        station.setChanged()
    }

    private fun assemble(native: Boolean = false) {
        check(pending.isEmpty())
        val ids = Create.RAILWAYS.trains.keys.toSet()
        station.assemble(UUID.randomUUID())
        train = Create.RAILWAYS.trains.values.singleOrNull { it.id !in ids }
            ?: error("Station assembly rejected: ${station.saveWithoutMetadata(source.registryAccess())}")
        val assembled = checkNotNull(train)
        check(assembled.carriages.size == 2)
        assembled.name = Component.literal("TeutonLib · Sable Express")
        assembled.carriages.forEach { it.manageEntities(source) }
        if (native) {
            // Change the captured carriage type before CTF has created
            // any train plot, storage baseline, or links for this carriage. This
            // leaves station geometry and its captured machine NBT identical.
            val setter = AbstractContraptionEntity::class.java.getDeclaredMethod("setContraption", Contraption::class.java)
                .apply { isAccessible = true }
            for (carriage in assembled.carriages) {
                val entity = checkNotNull(carriage.anyAvailableEntity())
                check(SubLevelLinks.linkFor(entity.uuid) == null) { "Native baseline must precede CTF conversion" }
                val previous = entity.contraption as CarriageContraption
                val storage = checkNotNull(carriage.storage)
                val cargo = CompoundTag().also { storage.write(it, source.registryAccess(), false) }
                val ordinary = CarriageContraption().apply {
                    readNBT(source, previous.writeNBT(source.registryAccess(), false), false)
                    portalCutoffMin = previous.portalCutoffMin
                    portalCutoffMax = previous.portalCutoffMax
                    swapStorageAfterAssembly(entity)
                }
                check(ordinary.javaClass == CarriageContraption::class.java)
                check(ordinary.blocks == previous.blocks) { "Native baseline changed captured block states or block entity NBT" }
                check(ordinary.anchor == previous.anchor && ordinary.bounds == previous.bounds) { "Native baseline changed wagon geometry" }
                check(ordinary.storage === storage && carriage.storage === storage) { "Native baseline replaced mounted cargo storage" }
                setter.invoke(entity, ordinary)
                check(entity.contraption === ordinary && ordinary.entity === entity)
                check(ordinary.actors.all { it.right == null || it.right.contraption === ordinary }) { "Native baseline retained stale actor contexts" }
                check(cargo == CompoundTag().also { storage.write(it, source.registryAccess(), false) }) { "Native baseline changed cargo" }
                check(SubLevelLinks.linkFor(entity.uuid) == null)
            }
        }
        check(station.exitAssemblyMode())
        if (native) {
            // Exercise CTF's own ordinary-train conversion on the next normal
            // server tick, without HybridTrainRuntime requesting the conversion.
            TrainConfig.HANDLING.set(TrainConfig.Handling.ALWAYS)
            ServerConfig.HYBRID_TRAINS.set(false)
            nativeBaseline = true
        }
    }

    private fun body(index: Int) = StationBuildRuntime.bodies(station).sortedBy { StationBuildRuntime.origin(it).x }[index]
    private fun queue(index: Int, offset: BlockPos, state: BlockState) {
        val body = body(index)
        pending.add { source.setBlockAndUpdate(body.plot.centerBlock.offset(offset), state); Unit }
    }

    private fun deck(index: Int) {
        val palette = if (index == 0) Blocks.CYAN_CONCRETE else Blocks.ORANGE_CONCRETE
        for (x in -2..9) for (z in -2..2) queue(index, BlockPos(x, 1, z),
            (if (z == -2 || z == 2) palette else Blocks.DARK_OAK_PLANKS).defaultBlockState())
        for (x in listOf(-2, 9)) for (z in -2..2) queue(index, BlockPos(x, 2, z), Blocks.IRON_BARS.defaultBlockState())
        queue(index, BlockPos(0, 2, -1), AllBlocks.TRAIN_CONTROLS.defaultState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST))
        queue(index, BlockPos(0, 2, 1), Blocks.CHEST.defaultBlockState())
    }

    private fun machines() {
        queue(0, mechanical.below(), AllBlocks.CREATIVE_MOTOR.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
        queue(0, mechanical, AllBlocks.MECHANICAL_BEARING.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
        queue(0, mechanical.above(), Blocks.SLIME_BLOCK.defaultBlockState())
        for (d in Direction.Plane.HORIZONTAL) queue(0, mechanical.above().relative(d), Blocks.CYAN_CONCRETE.defaultBlockState())
        queue(0, swivel, SimBlocks.SWIVEL_BEARING.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
        queue(0, swivel.above(), Blocks.CHEST.defaultBlockState())
        queue(0, swivel.east().below(), AllBlocks.CREATIVE_MOTOR.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
        queue(0, swivel.east(), AllBlocks.COGWHEEL.defaultState.setValue(BlockStateProperties.AXIS, Direction.Axis.Y))
        queue(1, radar.below(), AllBlocks.CREATIVE_MOTOR.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
        queue(1, radar, ModBlocks.RADAR_BEARING_BLOCK.defaultState)
        queue(1, radar.above(), ModBlocks.RADAR_RECEIVER_BLOCK.defaultState.setValue(BlockStateProperties.FACING, Direction.NORTH))
        queue(1, radar.above(2), ModBlocks.CREATIVE_RADAR_PLATE_BLOCK.defaultState)
        queue(1, BlockPos(7, 2, 2), Blocks.ORANGE_CONCRETE.defaultBlockState())
        queue(1, BlockPos(7, 3, 2), Blocks.ORANGE_CONCRETE.defaultBlockState())
        queue(1, BlockPos(7, 4, 2), Blocks.ORANGE_CONCRETE.defaultBlockState())
        queue(1, hose, AllBlocks.HOSE_PULLEY.defaultState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH))
        queue(1, hose.west(), AllBlocks.CREATIVE_MOTOR.defaultState.setValue(BlockStateProperties.FACING, Direction.EAST))
        pending.add { (source.getBlockEntity(body(1).plot.centerBlock.offset(hose).west()) as CreativeMotorBlockEntity).generatedSpeed.setValue(0) }
    }

    private fun startMachines() {
        val first = body(0).plot.centerBlock
        val second = body(1).plot.centerBlock
        (source.getBlockEntity(first.offset(mechanical)) as MechanicalBearingBlockEntity).assemble()
        (source.getBlockEntity(first.offset(swivel)) as SwivelBearingBlockEntity).let { it.assemble(); it.assembleNextTick = false }
        (source.getBlockEntity(second.offset(radar)) as RadarBearingBlockEntity).assemble()
        val pulley = source.getBlockEntity(second.offset(hose)) as HosePulleyBlockEntity
        (HosePulleyBlockEntity::class.java.getDeclaredField("offset").also { it.isAccessible = true }.get(pulley) as LerpedFloat).startWithValue(1.0)
        HosePulleyBridge.tick(pulley, 1f)
        pulley.sendData()
        for (i in 0..1) (source.getBlockEntity(body(i).plot.centerBlock.offset(0, 2, 1)) as ChestBlockEntity).setItem(0, ItemStack(Items.DIAMOND, 17 + i))
    }

    private fun route() {
        val train = checkNotNull(train)
        check(train.carriages.all { SubLevelLinks.linkFor(checkNotNull(it.anyAvailableEntity()).uuid) != null })
        val destination = TrainPortalTestDestination.level(source)
        for (x in 8..16) for (z in -2..2) destination.setChunkForced(x, z, true)
        val graph = TrackGraph()
        fun node(x: Double, level: ServerLevel) = TrackNode(TrackNodeLocation(x, 100.0, 0.0).`in`(level), TrackGraph.nextNodeId(), Vec3(0.0, 1.0, 0.0))
        val a = node(20.0, source); val b = node(99.5, source)
        val c = node(160.5, destination); val d = node(260.0, destination)
        listOf(a, b, c, d).forEach(graph::addNode)
        IPCreatePortalEdges.add(b.location, c.location)
        for ((f, s) in listOf(a to b, b to c, c to d)) {
            graph.putConnection(f, s, TrackEdge(f, s, null, TrackMaterial.ANDESITE))
            graph.putConnection(s, f, TrackEdge(s, f, null, TrackMaterial.ANDESITE))
        }
        val oldGraph = train.graph
        train.forEachTravellingPoint { point ->
            val x = point.getPosition(oldGraph).x
            val positive = point.node2.location.location.x > point.node1.location.location.x
            point.node1 = if (positive) a else b; point.node2 = if (positive) b else a
            point.edge = graph.getConnectionsFrom(point.node1).getValue(point.node2)
            point.position = if (positive) x - 20.0 else 99.5 - x
        }
        direction = if (train.carriages.first().leadingPoint.node1 === a) 1.0 else -1.0
        train.leaveStation(); train.graph = graph
        Create.RAILWAYS.trackNetworks[graph.id] = graph
        fun portal(from: ServerLevel, to: ServerLevel, x: Double, target: Double, reverse: Boolean) {
            val portal = Portal(Portal.ENTITY_TYPE, from)
            portal.setOriginPos(Vec3(x, 105.0, 0.0)); portal.destination = Vec3(target, 105.0, 0.0)
            portal.dimensionTo = to.dimension()
            portal.setOrientationAndSize(Vec3(0.0, 0.0, if (reverse) -1.0 else 1.0), Vec3(0.0, 1.0, 0.0), TrainDockingDemo.portalWidth, 14.0)
            from.addFreshEntity(portal)
        }
        portal(source, destination, 100.0, 160.0, false)
        portal(destination, source, 160.0, 100.0, true)
        source.server.playerList.players.forEach(Create.RAILWAYS::playerLogin)
    }

    internal fun sourceLevel(): ServerLevel = source

    internal fun roots(): List<Pair<SubLevelLinks.Link, ServerSubLevel>> = checkNotNull(train).carriages.map { carriage ->
        val owner = checkNotNull(carriage.anyAvailableEntity())
        val link = checkNotNull(SubLevelLinks.linkFor(owner.uuid))
        link to (SubLevelContainer.getContainer(source.server.getLevel(link.dimension)!!)!!.getSubLevel(link.subLevelId) as ServerSubLevel)
    }

    private fun verify() {
        val roots = roots().sortedBy { it.first.plotOrigin.asLong() }
        check(roots.size == 2)
        var mechanicalCount = 0; var swivelCount = 0; var radarCount = 0; var hoseCount = 0
        val ids = roots.map { it.first.subLevelId }.toSet()
        check(checkedBodies == null || checkedBodies == ids) { "The demo changed train bodies" }
        checkedBodies = ids
        var cargo = 0
        fun rotated(name: String, angle: Double) {
            angles.put(name, angle)?.let { check(kotlin.math.abs(angle - it) > .001) { "$name stopped rotating" } }
        }
        for ((_, sub) in roots) {
            check(HybridTrainRuntime.mode(sub) == HybridTrainRuntime.Mode.DRIVING)
            for (be in sub.plot.loadedChunks.flatMap { it.chunk.blockEntities.values }) when (be) {
                is RadarBearingBlockEntity -> {
                    check(be.isRunning && be.movedContraption?.isAlive == true)
                    check(be.movedContraption!!.contraption.blocks.size >= 2) { "Radar lost its payload: ${be.movedContraption!!.contraption.blocks}" }
                    rotated("radar", be.angle.toDouble()); radarCount++
                }
                is MechanicalBearingBlockEntity -> {
                    check(be.isRunning && be.movedContraption?.isAlive == true)
                    check(be.movedContraption!!.contraption.blocks.size >= 5) { "Mechanical bearing lost its payload: ${be.movedContraption!!.contraption.blocks}" }
                    rotated("mechanical", be.getInterpolatedAngle(1f).toDouble()); mechanicalCount++
                }
                is SwivelBearingBlockEntity -> { check(be.isAssembled && be.subLevelID != null); rotated("swivel", be.targetAngleDegrees); swivelCount++ }
                is HosePulleyBlockEntity -> {
                    val host = be as HosePulleyHost
                    val head = host.physicalHeadId?.let { (be.level as ServerLevel).getEntity(it) } as? HoseHeadEntity
                    check(head != null && head.isAlive && head.validOwner() === be); hoseCount++
                }
                is ChestBlockEntity -> cargo += be.getItem(0).count
            }
        }
        check(listOf(mechanicalCount, swivelCount, radarCount, hoseCount).all { it == 1 }) { "Machines: $mechanicalCount $swivelCount $radarCount $hoseCount" }
        check(cargo == 35) { "Wagon cargo changed: $cargo" }
    }

    private fun status(): String = failure?.let { "FAIL $it" } ?: "READY plots=${if (::station.isInitialized) StationBuildRuntime.bodies(station).size else 0} queued=${pending.size} train=${train?.id} travel=$travel " +
        (train?.carriages?.joinToString(";") { "${it.leadingPoint.node1.location.dimension.location()}:${it.leadingPoint.getPosition(train!!.graph)}" } ?: "")

    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if (!::source.isInitialized || failure != null) return
        try {
            repeat(2) { pending.removeFirstOrNull()?.invoke() }
            val train = train ?: return
            if (nativeBaseline) {
                check(!ServerConfig.hybridTrains())
                check(train.carriages.all { it.anyAvailableEntity()?.contraption?.javaClass == CarriageContraption::class.java }) {
                    "Native Sable comparison lost its ordinary carriage type"
                }
            }
            if (kotlin.math.abs(travel) > 1e-6) {
                val delta = travel.coerceIn(-.16, .16)
                val carriages = if (delta * direction >= 0) train.carriages else train.carriages.reversed()
                for (carriage in carriages) carriage.travel(source, train.graph, delta * direction, null, null, 3)
                travel -= delta
            }
            for (level in event.server.allLevels) {
                AutoConvert.tick(level)
                if (nativeBaseline) for (carriage in train.carriages) {
                    val entity = carriage.anyAvailableEntity() ?: continue
                    val link = SubLevelLinks.linkFor(entity.uuid) ?: continue
                    if (link.dimension != level.dimension() || link.subLevelId in nativeAdopted) continue
                    val body = SubLevelContainer.getContainer(level)?.getSubLevel(link.subLevelId) as? ServerSubLevel ?: continue
                    // HybridRuntime's conversion hook is disabled in this case.
                    // Resume the exact station-captured machine records once,
                    // using the same restoration path as the comparison train.
                    de.TeutonStudio.TeutonLib.integration.train.TrainPortalMachines.adopt(body)
                    nativeAdopted += link.subLevelId
                }
                SubLevelLinks.syncAll(level)
            }
            TrainDockingDemo.observe()
        } catch (e: Throwable) { failure = e.stackTraceToString(); org.slf4j.LoggerFactory.getLogger("TeutonLib/TrainDemo").error("Demo failed", e) }
    }
}
