package de.TeutonStudio.TeutonLib.mekatest

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.simibubi.create.AllBlocks
import com.simibubi.create.Create
import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.CarriageBogey
import com.simibubi.create.content.trains.entity.CarriageContraption
import com.simibubi.create.content.trains.entity.Train
import com.simibubi.create.content.trains.entity.TravellingPoint
import com.simibubi.create.content.trains.graph.TrackEdge
import com.simibubi.create.content.trains.graph.TrackGraph
import com.simibubi.create.content.trains.graph.TrackNode
import com.simibubi.create.content.trains.graph.TrackNodeLocation
import com.simibubi.create.content.trains.track.TrackMaterial
import createtrainfunctionality.SubLevelLinks
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import mekanism.common.lib.transmitter.ConnectionType
import mekanism.common.item.ItemConfigurator
import mekanism.common.network.PacketUtils
import mekanism.common.registries.MekanismBlocks
import mekanism.common.registries.MekanismDataComponents
import mekanism.common.registries.MekanismItems
import mekanism.common.tile.transmitter.TileEntityTransmitter
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

/** Opt-in real Mekanism/CTF fixture. Its world is created and removed by the verification runner. */
@Mod("teutonlib_mekanism_model_test")
class MekanismModelVerification {
    init { NeoForge.EVENT_BUS.register(this) }

    @SubscribeEvent
    fun commands(event: RegisterCommandsEvent) {
        val command = Commands.literal("teutonlib_mekanism_models").requires { it.hasPermission(4) }
        for (action in listOf("start", "convert", "swap", "move-swap", "status")) {
            command.then(Commands.literal(action).executes { context ->
                val state = MekanismModelFixture.command(context.source.level, action)
                context.source.sendSuccess({ Component.literal(state) }, false)
                1
            })
        }
        event.dispatcher.register(command)
    }

    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) = MekanismModelFixture.tick(event.server)
}

object MekanismModelFixture {
    private val log = LoggerFactory.getLogger("TeutonLib/MekanismModelTest")
    private val gson = Gson()
    private val controlOrigin = BlockPos(32, 100, 32)
    private val sourceOrigin = BlockPos(80, 100, 32)
    private val pipeOffset = BlockPos(0, 2, 0)
    private val offsets = buildList {
        for (x in -2..2) for (z in -1..1) add(BlockPos(x, 0, z))
        for (x in -1..1) add(BlockPos(x, 1, 0))
        for (x in -1..1) add(BlockPos(x, 2, 0))
    }
    private data class Run(val level: ServerLevel, var stage: String = "WORLD_WAIT", var wait: Int = 20,
        var ship: UUID? = null, var plotOrigin: BlockPos? = null, var failure: String? = null,
        var train: Train? = null, var graph: TrackGraph? = null, var ticks: Int = 0) {
        var movementTicks = 0
    }
    private var run: Run? = null
    private var setupFailure: String? = null

    fun command(level: ServerLevel, action: String): String {
        try {
            when (action) {
                "start" -> start(level)
                "convert" -> convert(checkNotNull(run))
                "swap" -> swap(checkNotNull(run))
                "move-swap" -> moveSwap(checkNotNull(run))
                "status" -> Unit
                else -> error("Unknown action $action")
            }
        } catch (failure: Throwable) { fail(failure) }
        val state = state()
        run?.level?.server?.playerList?.players?.forEach {
            it.sendSystemMessage(Component.literal("TEUTONLIB_MEKANISM_STATE $state"))
        }
        return state
    }

    private fun start(level: ServerLevel) {
        check(run == null) { "Fixture already exists" }
        run = Run(level)
        for (origin in listOf(controlOrigin, sourceOrigin)) {
            for (cx in (origin.x - 32 shr 4)..(origin.x + 32 shr 4)) {
                for (cz in (origin.z - 32 shr 4)..(origin.z + 32 shr 4)) level.setChunkForced(cx, cz, true)
            }
            for (offset in offsets) {
                val state = when {
                    offset == pipeOffset -> MekanismBlocks.BASIC_MECHANICAL_PIPE.get().defaultBlockState()
                    offset.y == 2 -> MekanismBlocks.BASIC_FLUID_TANK.get().defaultBlockState()
                    offset == BlockPos.ZERO -> AllBlocks.SMALL_BOGEY.defaultState
                    offset.y == 1 -> Blocks.COPPER_BLOCK.defaultBlockState()
                    offset.x < 0 -> Blocks.YELLOW_CONCRETE.defaultBlockState()
                    else -> Blocks.CYAN_CONCRETE.defaultBlockState()
                }
                level.setBlockAndUpdate(origin.offset(offset), state)
            }
        }
    }

    private fun convert(active: Run) {
        check(active.stage == "WORLD_READY") { "Expected WORLD_READY, got ${active.stage}" }
        assertModes(tile(active.level, controlOrigin.offset(pipeOffset)), ConnectionType.PULL, ConnectionType.PUSH)
        assertModes(tile(active.level, sourceOrigin.offset(pipeOffset)), ConnectionType.PULL, ConnectionType.PUSH)
        val graph = TrackGraph()
        val a = TrackNode(TrackNodeLocation(50.0, 100.0, 32.5).`in`(active.level),
            TrackGraph.nextNodeId(), Vec3(0.0, 1.0, 0.0))
        val b = TrackNode(TrackNodeLocation(130.0, 100.0, 32.5).`in`(active.level),
            TrackGraph.nextNodeId(), Vec3(0.0, 1.0, 0.0))
        graph.addNode(a); graph.addNode(b)
        val edge = TrackEdge(a, b, null, TrackMaterial.ANDESITE)
        graph.putConnection(a, b, edge)
        graph.putConnection(b, a, TrackEdge(b, a, null, TrackMaterial.ANDESITE))
        Create.RAILWAYS.trackNetworks[graph.id] = graph
        val carriage = Carriage(CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, CompoundTag(),
            TravellingPoint(a, b, edge, 32.0, false), TravellingPoint(a, b, edge, 30.0, false)), null, 0)
        val contraption = CarriageContraption(Direction.EAST).also {
            it.anchor = BlockPos.ZERO
            it.bounds = AABB(-2.0, 0.0, -1.0, 3.0, 3.0, 2.0)
            for (offset in offsets) {
                val position = sourceOrigin.offset(offset)
                val tag = active.level.getBlockEntity(position)?.saveWithFullMetadata(active.level.registryAccess())
                it.blocks[offset] = StructureBlockInfo(offset, active.level.getBlockState(position), tag)
            }
            it.storage.initialize()
        }
        // Source tags above come from real, settled block entities, including the configured modes.
        for (offset in offsets.asReversed()) active.level.setBlock(sourceOrigin.offset(offset), Blocks.AIR.defaultBlockState(), 2)
        val train = Train(UUID.randomUUID(), UUID.randomUUID(), graph, mutableListOf(carriage), mutableListOf(), false, 0)
        Create.RAILWAYS.addTrain(train)
        carriage.setContraption(active.level, contraption)
        carriage.manageEntities(active.level)
        if (carriage.anyAvailableEntity() == null) {
            val record = carriage.getDimensional(active.level)
            record.javaClass.getDeclaredMethod("createEntity", Level::class.java, Boolean::class.javaPrimitiveType)
                .also { it.isAccessible = true }.invoke(record, active.level, true)
        }
        val entity = checkNotNull(carriage.anyAvailableEntity()) { "Create did not spawn fixture carriage" }
        val link = checkNotNull(SubLevelLinks.convert(active.level, entity)) { "CTF conversion failed" }
        active.ship = link.subLevelId
        active.plotOrigin = link.plotOrigin
        active.train = train
        active.graph = graph
        active.stage = "CONVERT_WAIT"
        active.wait = 20
    }

    private fun swap(active: Run) {
        check(active.stage == "CONVERT_READY") { "Expected CONVERT_READY, got ${active.stage}" }
        val pipe = tile(active.level, checkNotNull(active.plotOrigin).offset(pipeOffset))
        assertModes(pipe, ConnectionType.PULL, ConnectionType.PUSH)
        // Invoke the exact native handler used by a crouching Configurator click. Its raycast and
        // CMC's mounted raycast hooks choose the face; no direct connection setter masks a failure.
        configure(pipe, Direction.WEST, ConnectionType.PUSH, active)
        configure(pipe, Direction.EAST, ConnectionType.PULL, active)
        active.stage = "SWAP_WAIT"
        active.wait = 20
    }

    private fun moveSwap(active: Run) {
        check(active.stage == "SWAP_READY") { "Expected SWAP_READY, got ${active.stage}" }
        active.stage = "MOVE_WAIT"
        active.wait = 60
        active.movementTicks = 0
    }

    fun tick(server: MinecraftServer) {
        val active = run ?: return
        if (active.failure != null || active.wait <= 0) return
        try {
            check(++active.ticks < 600) { "Fixture stage timed out: ${active.stage}" }
            if (active.stage == "MOVE_WAIT" && active.movementTicks < 40) {
                val carriage = checkNotNull(active.train).carriages.single()
                carriage.travel(active.level, checkNotNull(active.graph), 0.6, null, null, 3)
                SubLevelLinks.syncAll(active.level)
                active.movementTicks++
                if (active.movementTicks == 20) {
                    val pipe = tile(active.level, checkNotNull(active.plotOrigin).offset(pipeOffset))
                    configure(pipe, Direction.WEST, ConnectionType.PULL, active, useItem = true)
                    configure(pipe, Direction.EAST, ConnectionType.PUSH, active, useItem = true)
                }
            }
            active.ship?.let { SubLevelLinks.syncAll(active.level) }
            if (--active.wait > 0) return
            when (active.stage) {
                "WORLD_WAIT" -> {
                    // Wait for the initial chunks to finish sending before exercising native mode
                    // packets. Otherwise a pre-configuration chunk snapshot can arrive afterwards.
                    if (listOf(controlOrigin, sourceOrigin).any {
                        !PacketUtils.hasPlayersTracking(active.level, it.offset(pipeOffset))
                    }) {
                        active.wait = 10
                        return
                    }
                    for (origin in listOf(controlOrigin, sourceOrigin)) {
                        val pipe = tile(active.level, origin.offset(pipeOffset))
                        configure(pipe, Direction.WEST, ConnectionType.PULL, null)
                        configure(pipe, Direction.EAST, ConnectionType.PUSH, null)
                    }
                    active.stage = "WORLD_CONFIG_WAIT"; active.wait = 20
                }
                "WORLD_CONFIG_WAIT" -> {
                    for (origin in listOf(controlOrigin, sourceOrigin))
                        assertModes(tile(active.level, origin.offset(pipeOffset)), ConnectionType.PULL, ConnectionType.PUSH)
                    ready(active, "WORLD_READY")
                }
                "CONVERT_WAIT" -> {
                    check(SubLevelContainer.getContainer(active.level)?.getSubLevel(checkNotNull(active.ship)) is ServerSubLevel)
                    assertModes(tile(active.level, checkNotNull(active.plotOrigin).offset(pipeOffset)), ConnectionType.PULL, ConnectionType.PUSH)
                    ready(active, "CONVERT_READY")
                }
                "SWAP_WAIT" -> {
                    assertModes(tile(active.level, checkNotNull(active.plotOrigin).offset(pipeOffset)), ConnectionType.PUSH, ConnectionType.PULL)
                    assertModes(tile(active.level, controlOrigin.offset(pipeOffset)), ConnectionType.PULL, ConnectionType.PUSH)
                    ready(active, "SWAP_READY")
                }
                "MOVE_WAIT" -> {
                    check(active.movementTicks == 40)
                    assertModes(tile(active.level, checkNotNull(active.plotOrigin).offset(pipeOffset)), ConnectionType.PULL, ConnectionType.PUSH)
                    assertModes(tile(active.level, controlOrigin.offset(pipeOffset)), ConnectionType.PULL, ConnectionType.PUSH)
                    ready(active, "MOVING_READY")
                }
                else -> error("Unexpected waiting stage ${active.stage}")
            }
        } catch (failure: Throwable) { fail(failure) }
    }

    private fun configure(pipe: TileEntityTransmitter, side: Direction, target: ConnectionType, active: Run?, useItem: Boolean = false) {
        val level = pipe.level as ServerLevel
        val fake = FakePlayerFactory.getMinecraft(level)
        val plotEye = Vec3.atCenterOf(pipe.blockPos).add(Vec3.atLowerCornerOf(side.normal).scale(4.0))
        val plotTarget = Vec3.atCenterOf(pipe.blockPos)
        val sub = active?.ship?.let { SubLevelContainer.getContainer(level)?.getSubLevel(it) as? ServerSubLevel }
        val eye = sub?.logicalPose()?.transformPosition(plotEye) ?: plotEye
        val targetPoint = sub?.logicalPose()?.transformPosition(plotTarget) ?: plotTarget
        val delta = targetPoint.subtract(eye)
        fake.setPos(eye.x, eye.y - fake.eyeHeight, eye.z)
        fake.yRot = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        fake.xRot = Math.toDegrees(-atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z))).toFloat()
        fake.setShiftKeyDown(true)
        val previousItem = fake.getItemInHand(InteractionHand.MAIN_HAND)
        val configurator = MekanismItems.CONFIGURATOR.get()
        if (useItem) fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(configurator).also {
            it.set(MekanismDataComponents.CONFIGURATOR_MODE.get(), ItemConfigurator.ConfiguratorMode.CONFIGURATE_FLUIDS)
        })
        val stablePlot = active?.plotOrigin?.offset(pipeOffset) ?: pipe.blockPos
        val physical = BlockPos.containing(sub?.logicalPose()?.transformPosition(Vec3.atCenterOf(pipe.blockPos))
            ?: Vec3.atCenterOf(pipe.blockPos))
        try {
            var clicks = 0
            while (pipe.transmitter.getConnectionTypeRaw(side) != target && clicks++ < 4) {
                val before = pipe.transmitter.getConnectionTypeRaw(side)
                log.info("Mekanism Configurator useItem={} side={} before={} target={} tile={} plot={} physical={} plotTracked={} physicalTracked={}",
                    useItem, side, before, target, pipe.blockPos, stablePlot, physical,
                    PacketUtils.hasPlayersTracking(level, stablePlot), PacketUtils.hasPlayersTracking(level, physical))
                if (useItem) {
                    val hit = BlockHitResult(Vec3.atCenterOf(stablePlot).add(Vec3.atLowerCornerOf(side.normal).scale(0.4)),
                        side, stablePlot, false)
                    val result = configurator.useOn(UseOnContext(fake, InteractionHand.MAIN_HAND, hit))
                    check(result.consumesAction()) { "Configurator item rejected mounted interaction: $result" }
                } else pipe.onSneakRightClick(fake, side)
                check(pipe.transmitter.getConnectionTypeRaw(side) != before) { "Configurator did not change $side" }
            }
        } finally {
            fake.setShiftKeyDown(false)
            fake.setItemInHand(InteractionHand.MAIN_HAND, previousItem)
        }
        check(pipe.transmitter.getConnectionTypeRaw(side) == target) { "Configurator did not select $side=$target" }
    }

    private fun tile(level: ServerLevel, position: BlockPos): TileEntityTransmitter =
        level.getBlockEntity(position) as? TileEntityTransmitter ?: error("Missing transmitter at $position")

    private fun assertModes(pipe: TileEntityTransmitter, west: ConnectionType, east: ConnectionType) {
        for ((side, expected) in listOf(Direction.WEST to west, Direction.EAST to east)) {
            check(pipe.transmitter.getConnectionTypeRaw(side) == expected) { "$side raw mode changed" }
            check(pipe.transmitter.getConnectionType(side) == expected) { "$side not connected to adjacent tank" }
        }
        val provider = checkNotNull(pipe.level).registryAccess()
        for (tag in listOf(pipe.saveWithFullMetadata(provider), pipe.getUpdateTag(provider))) {
            check(tag.getIntArray("connection")[Direction.WEST.ordinal] == west.ordinal)
            check(tag.getIntArray("connection")[Direction.EAST.ordinal] == east.ordinal)
        }
    }

    private fun ready(active: Run, stage: String) {
        active.stage = stage
        log.info("Mekanism models PASS stage={} {}", stage, state())
        active.level.server.playerList.players.forEach {
            it.sendSystemMessage(Component.literal("TEUTONLIB_MEKANISM_STATE ${state()}"))
        }
    }

    private fun fail(failure: Throwable) {
        setupFailure = failure.stackTraceToString()
        run?.also { it.stage = "FAIL"; it.failure = failure.stackTraceToString(); it.wait = 0 }
        log.error("Mekanism models FAIL", failure)
    }

    private fun coordinates(position: BlockPos) = JsonArray().also {
        it.add(position.x); it.add(position.y); it.add(position.z)
    }

    private fun state(): String = gson.toJson(JsonObject().also { json ->
        val active = run
        val failure = active?.failure ?: setupFailure
        json.addProperty("state", when {
            failure != null -> "FAIL"
            active?.stage?.endsWith("_READY") == true -> "READY"
            else -> "RUNNING"
        })
        json.addProperty("stage", active?.stage ?: if (failure != null) "FAIL" else "IDLE")
        json.addProperty("passed", active?.stage?.endsWith("_READY") == true)
        json.addProperty("waitTicks", active?.wait ?: 0)
        active?.let {
            json.addProperty("controlTracked", PacketUtils.hasPlayersTracking(it.level, controlOrigin.offset(pipeOffset)))
            json.addProperty("sourceTracked", PacketUtils.hasPlayersTracking(it.level, sourceOrigin.offset(pipeOffset)))
            json.addProperty("controlMergedViewers", it.level.chunkSource.chunkMap.getPlayers(ChunkPos(controlOrigin), false).size)
            json.addProperty("sourceMergedViewers", it.level.chunkSource.chunkMap.getPlayers(ChunkPos(sourceOrigin), false).size)
            val chunkMap = it.level.chunkSource.chunkMap
            val playerMap = chunkMap.javaClass.getDeclaredField("playerMap").also { field -> field.isAccessible = true }.get(chunkMap)
            val players = playerMap.javaClass.getDeclaredMethod("getAllPlayers").also { method -> method.isAccessible = true }.invoke(playerMap) as Set<*>
            json.addProperty("vanillaTrackingPlayerCount", players.size)
        }
        json.add("controlPipe", coordinates(controlOrigin.offset(pipeOffset)))
        json.add("worldPipe", coordinates(controlOrigin.offset(pipeOffset)))
        json.add("sourcePipe", coordinates(sourceOrigin.offset(pipeOffset)))
        active?.plotOrigin?.let { json.add("mountedPipe", coordinates(it.offset(pipeOffset))) }
        val pipe = active?.plotOrigin?.offset(pipeOffset) ?: sourceOrigin.offset(pipeOffset)
        json.add("pipe", coordinates(pipe))
        json.addProperty("ship", active?.ship?.toString())
        active?.ship?.let { json.addProperty("shipUuid", it.toString()) }
        val sub = active?.ship?.let { SubLevelContainer.getContainer(active.level)?.getSubLevel(it) as? ServerSubLevel }
        active?.let {
            json.addProperty("pipeTracked", PacketUtils.hasPlayersTracking(it.level, pipe))
            json.addProperty("pipeMergedViewers", it.level.chunkSource.chunkMap.getPlayers(ChunkPos(pipe), false).size)
            json.addProperty("movementTicks", it.movementTicks)
            (it.level.getBlockEntity(pipe) as? TileEntityTransmitter)?.let { tile ->
                json.add("tilePipe", coordinates(tile.blockPos))
                val ownerViaTile = Sable.HELPER.getContaining(tile)
                json.addProperty("ownerViaTile", ownerViaTile?.javaClass?.name)
                json.addProperty("ownerViaTileUuid", ownerViaTile?.uniqueId?.toString())
                json.addProperty("levelTileIdentity", it.level.getBlockEntity(tile.blockPos) === tile)
                json.addProperty("tileLevelClass", tile.level?.javaClass?.name)
                val physical = BlockPos.containing(sub?.logicalPose()?.transformPosition(Vec3.atCenterOf(tile.blockPos))
                    ?: Vec3.atCenterOf(tile.blockPos))
                json.add("physicalPipe", coordinates(physical))
                json.addProperty("physicalTracked", PacketUtils.hasPlayersTracking(it.level, physical))
            }
        }
        val target = sub?.logicalPose()?.transformPosition(Vec3.atCenterOf(pipe)) ?: Vec3.atCenterOf(pipe)
        val feet = target.add(4.0, 1.5, 7.0)
        val delta = target.subtract(feet.add(0.0, 1.62, 0.0))
        json.add("camera", JsonArray().also {
            it.add(feet.x); it.add(feet.y); it.add(feet.z)
            it.add(Math.toDegrees(atan2(-delta.x, delta.z)))
            it.add(Math.toDegrees(-atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z))))
        })
        failure?.let { json.addProperty("failure", it) }
    })
}
