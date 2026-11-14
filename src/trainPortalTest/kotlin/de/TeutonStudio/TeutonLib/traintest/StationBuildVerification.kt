package de.TeutonStudio.TeutonLib.traintest

import com.simibubi.create.AllBlocks
import com.simibubi.create.Create
import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.track.TrackBlock
import com.simibubi.create.content.trains.track.TrackShape
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridCarriageContraption
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.Properties
import java.nio.file.Files
import net.minecraft.world.level.storage.LevelResource
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity

/** Real station enter/trackClicked/assemble paths, without a prebuilt carriage. */
object StationBuildVerification {
    private var station: StationBlockEntity? = null
    private var ticks = 0
    private var phase = 0
    private var result = "IDLE"
    private val trackStart = BlockPos(640, 180, 64)
    private var first: UUID? = null
    private val checkpointStart = trackStart.east(128)
    private val bearingOffset = BlockPos(3, 2, 0)

    private fun createStation(level: ServerLevel, start: BlockPos): StationBlockEntity {
        for (x in ((start.x - 16) shr 4)..((start.x + 80) shr 4)) for (z in 3..5) level.setChunkForced(x, z, true)
        for (x in -8..72) {
            level.setBlockAndUpdate(start.east(x).below(), Blocks.STONE.defaultBlockState())
            level.setBlockAndUpdate(start.east(x), AllBlocks.TRACK.defaultState.setValue(TrackBlock.SHAPE, TrackShape.XO))
        }
        val pos = start.north(3)
        level.setBlockAndUpdate(pos, AllBlocks.TRACK_STATION.defaultState)
        val placed = level.getBlockEntity(pos) as StationBlockEntity
        TrackTargetingBehaviour::class.java.getDeclaredField("targetTrack").also { it.isAccessible = true }
            .set(placed.edgePoint, start.subtract(pos))
        TrackTargetingBehaviour::class.java.getDeclaredField("targetDirection").also { it.isAccessible = true }
            .set(placed.edgePoint, Direction.AxisDirection.POSITIVE)
        placed.setChanged()
        return placed
    }

    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("teutonlib_station_build_test").requires { it.hasPermission(4) }
            .then(Commands.literal("start").executes { ctx ->
                val level = ctx.source.level
                station = createStation(level, trackStart); ticks = 0; phase = 0; result = "RUNNING waiting for native track graph"
                ctx.source.sendSuccess({ Component.literal(result) }, false); 1
            }).then(Commands.literal("status").executes { ctx ->
                ctx.source.sendSuccess({ Component.literal(result) }, false); 1
            }).then(Commands.literal("loaded").executes { ctx ->
                val message = try {
                    val level = ctx.source.level
                    val values = Properties().also { Files.newBufferedReader(level.server.getWorldPath(LevelResource.ROOT)
                        .resolve("station-build-checkpoint.properties")).use(it::load) }
                    val placed = level.getBlockEntity(BlockPos.of(values.getProperty("station").toLong())) as? StationBlockEntity
                    val body = placed?.let(StationBuildRuntime::bodies)?.firstOrNull { it.uniqueId.toString() == values.getProperty("body") }
                    if (body == null || placed.station == null || placed.edgePoint.determineGraphLocation() == null)
                        "RUNNING loading station construction and native graph"
                    else {
                        val chest = level.getBlockEntity(body.plot.centerBlock.above()) as ChestBlockEntity
                        check(chest.getItem(0).`is`(Items.EMERALD) && chest.getItem(0).count == 23) { "Saved cargo changed" }
                        check(!body.isRemoved && !StationBuildRuntime.dormant(body)) { "Occupied construction not awake after load" }
                        check(placed.exitAssemblyMode()) { "Native cancellation rejected after graph load" }
                        val returned = level.getBlockEntity(checkpointStart.east().above(2)) as ChestBlockEntity
                        check(returned.getItem(0).count == 23 && StationBuildRuntime.bodies(placed).isEmpty())
                        "PASS station restart: same construction plot UUID; live cargo; cancellation returns all blocks and inventory"
                    }
                } catch (failure: Throwable) { "FAIL station restart $failure" }
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }))
    }

    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        val station = station ?: return
        val level = station.level as ServerLevel
        try {
            ticks++
            check(ticks < 240) { "Timed out: $result" }
            when (phase) {
                0 -> {
                    if (ticks < 15 || station.station == null) return
                    check(station.enterAssemblyMode(null))
                    check(StationBuildRuntime.bodies(station).size == 1) { "New train did not create a plot" }
                    phase = 10; ticks = 0; result = "RUNNING empty construction plot survives before the first bogey"
                }
                10 -> if (ticks >= 15) {
                    check(StationBuildRuntime.bodies(station).size == 1) { "Empty construction plot disappeared" }
                    val player = FakePlayerFactory.getMinecraft(level)
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AllBlocks.RAILWAY_CASING.asItem(), 16))
                    for (offset in listOf(1, 7, 15)) {
                        val pos = trackStart.east(offset)
                        check(station.trackClicked(player, InteractionHand.MAIN_HAND, AllBlocks.TRACK.get(), level.getBlockState(pos), pos)) {
                            "Native bogey placement failed at $pos"
                        }
                        check(level.getBlockState(pos.above()).isAir) { "Bogey remained outside Sable" }
                        check(StationBuildRuntime.state(station, level, pos.above()).block is com.simibubi.create.content.trains.bogey.AbstractBogeyBlock<*>)
                    }
                    val bodies = StationBuildRuntime.bodies(station)
                    check(bodies.size == 2) { "Third bogey did not start the next wagon: ${bodies.size}" }
                    first = bodies.first().uniqueId
                    for ((index, body) in bodies.withIndex()) {
                        val chestPos = body.plot.centerBlock.offset(0, 1, 2)
                        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState())
                        (level.getBlockEntity(chestPos) as ChestBlockEntity).setItem(0, ItemStack(Items.DIAMOND, 17 + index))
                    }
                    val cargoBefore = bodies.associateWith { body ->
                        (level.getBlockEntity(body.plot.centerBlock.offset(0, 1, 2)) as ChestBlockEntity).saveWithFullMetadata(level.registryAccess())
                    }
                    val trainIds = Create.RAILWAYS.trains.keys.toSet()
                    station.assemble(UUID.randomUUID())
                    check(Create.RAILWAYS.trains.keys == trainIds) { "Station accepted a train without controls" }
                    check(bodies.all { !it.isRemoved && StationBuildRuntime.blocks(it).isNotEmpty() }) {
                        "Native assembly failure destroyed a construction plot"
                    }
                    for ((body, expected) in cargoBefore) check(
                        (level.getBlockEntity(body.plot.centerBlock.offset(0, 1, 2)) as ChestBlockEntity)
                            .saveWithFullMetadata(level.registryAccess()) == expected) { "Failed assembly changed cargo NBT" }
                    for ((index, body) in bodies.withIndex()) {
                        val at = body.plot.centerBlock
                        level.setBlockAndUpdate(at.above(), Blocks.STONE.defaultBlockState())
                        level.setBlockAndUpdate(at.above(2), AllBlocks.TRAIN_CONTROLS.defaultState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST))
                        level.setBlockAndUpdate(at.offset(0, 1, 2), Blocks.CHEST.defaultBlockState())
                        (level.getBlockEntity(at.offset(0, 1, 2)) as ChestBlockEntity).setItem(0, ItemStack(Items.DIAMOND, 17 + index))
                        level.setBlockAndUpdate(at.offset(bearingOffset).below(), AllBlocks.CREATIVE_MOTOR.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
                        level.setBlockAndUpdate(at.offset(bearingOffset), AllBlocks.MECHANICAL_BEARING.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
                        level.setBlockAndUpdate(at.offset(bearingOffset).above(), Blocks.GLASS.defaultBlockState())
                        (level.getBlockEntity(at.offset(bearingOffset)) as MechanicalBearingBlockEntity).assemble()
                    }
                    phase = 1; ticks = 0; result = "RUNNING disconnected bogeys and live cargo on pinned plots"
                }
                1 -> if (ticks >= 35) {
                    val bodies = StationBuildRuntime.bodies(station)
                    check(bodies.size == 2 && bodies.any { it.uniqueId == first }) { "Construction split into extra plots" }
                    for (body in bodies) {
                        check((level.getBlockEntity(body.plot.centerBlock.offset(bearingOffset)) as MechanicalBearingBlockEntity).isRunning)
                        val world = StationBuildRuntime.origin(body)
                        val projected = body.logicalPose().transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(body.plot.centerBlock))
                        check(projected.distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(world)) < .05) { "Station build moved: $projected vs $world" }
                    }
                    // Preflight must leave both plots and cargo untouched on obstruction.
                    level.setBlockAndUpdate(trackStart.east().above(), Blocks.BEDROCK.defaultBlockState())
                    station.assemble(UUID.randomUUID())
                    check(StationBuildRuntime.blocks(bodies.first()).isNotEmpty())
                    check(level.getBlockState(trackStart.east().above()).`is`(Blocks.BEDROCK))
                    check(!station.exitAssemblyMode() && station.isAssembling) { "Blocked cancellation lost construction ownership" }
                    for (body in bodies) check((level.getBlockEntity(body.plot.centerBlock.offset(bearingOffset)) as MechanicalBearingBlockEntity).isRunning) {
                        "Obstructed assembly stopped running machinery"
                    }
                    level.setBlockAndUpdate(trackStart.east().above(), Blocks.AIR.defaultBlockState())
                    for (body in bodies) level.setBlockAndUpdate(body.plot.centerBlock.above(2), Blocks.AIR.defaultBlockState())
                    val before = Create.RAILWAYS.trains.keys.toSet()
                    station.assemble(UUID.randomUUID())
                    check(Create.RAILWAYS.trains.keys == before) { "Running machinery bypassed native controls requirement" }
                    phase = 11; ticks = 0; result = "RUNNING restoring cargo and active bearings after native assembly rejection"
                }
                11 -> if (ticks >= 20) {
                    val bodies = StationBuildRuntime.bodies(station)
                    for ((index, body) in bodies.withIndex()) {
                        val bearing = level.getBlockEntity(body.plot.centerBlock.offset(bearingOffset)) as MechanicalBearingBlockEntity
                        check(bearing.isRunning && bearing.movedContraption!!.contraption.blocks.values.any { it.state().`is`(Blocks.GLASS) }) {
                            "Failed assembly lost active bearing payload"
                        }
                        check((level.getBlockEntity(body.plot.centerBlock.offset(0, 1, 2)) as ChestBlockEntity).getItem(0).count == 17 + index)
                        level.setBlockAndUpdate(body.plot.centerBlock.above(2), AllBlocks.TRAIN_CONTROLS.defaultState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST))
                    }
                    val before = Create.RAILWAYS.trains.keys.toSet()
                    station.assemble(UUID.randomUUID())
                    val train = Create.RAILWAYS.trains.values.singleOrNull { it.id !in before }
                        ?: error("Native station assembly did not create a train: ${station.saveWithoutMetadata(level.registryAccess())}")
                    check(train.carriages.size == 2)
                    for (carriage in train.carriages) {
                        carriage.manageEntities(level)
                        val entity = carriage.anyAvailableEntity()
                        if (entity != null) check(entity.contraption is HybridCarriageContraption)
                    }
                    phase = 2; ticks = 0; result = "RUNNING checking assembled train cargo"
                }
                2 -> if (ticks >= 15) {
                    val links = SubLevelLinks.links().filter { it.dimension == level.dimension() && it.plotOrigin.let { origin ->
                        level.getBlockEntity(origin.offset(0, 1, 2)) is ChestBlockEntity
                    } }
                    val cargo = links.map { (level.getBlockEntity(it.plotOrigin.offset(0, 1, 2)) as ChestBlockEntity).getItem(0).count }
                    check(17 in cargo && 18 in cargo) { "Station assembly lost cargo: $cargo" }
                    for (link in links) {
                        val bearing = level.getBlockEntity(link.plotOrigin.offset(bearingOffset)) as MechanicalBearingBlockEntity
                        check(bearing.isRunning && bearing.movedContraption!!.contraption.blocks.values.any { it.state().`is`(Blocks.GLASS) }) {
                            "Station assembly did not resume the running bearing and its payload"
                        }
                    }
                    check(station.exitAssemblyMode())
                    check(StationBuildRuntime.bodies(station).isEmpty())
                    StationBuildVerification.station = createStation(level, checkpointStart)
                    phase = 3; ticks = 0; result = "RUNNING construction restart checkpoint"
                }
                3 -> if (ticks >= 15 && station.station != null) {
                    check(station.enterAssemblyMode(null))
                    val player = FakePlayerFactory.getMinecraft(level)
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AllBlocks.RAILWAY_CASING.asItem(), 4))
                    val pos = checkpointStart.east()
                    check(station.trackClicked(player, InteractionHand.MAIN_HAND, AllBlocks.TRACK.get(), level.getBlockState(pos), pos))
                    val body = StationBuildRuntime.bodies(station).single()
                    level.setBlockAndUpdate(body.plot.centerBlock.above(), Blocks.CHEST.defaultBlockState())
                    (level.getBlockEntity(body.plot.centerBlock.above()) as ChestBlockEntity).setItem(0, ItemStack(Items.EMERALD, 23))
                    val values = Properties().also { it["station"] = station.blockPos.asLong().toString(); it["body"] = body.uniqueId.toString() }
                    Files.newBufferedWriter(level.server.getWorldPath(LevelResource.ROOT).resolve("station-build-checkpoint.properties")).use {
                        values.store(it, "Disposable station construction checkpoint")
                    }
                    result = "PASS new train creates plot; native bogey placement; two bogeys per wagon; no splitting; fixed pose; native failure rollback; obstruction preflight; native two-wagon assembly; cargo and running bearing payload preserved; construction checkpoint"
                    StationBuildVerification.station = null
                }
            }
        } catch (failure: Throwable) {
            result = "FAIL station build phase=$phase ticks=$ticks $failure"
            LoggerFactory.getLogger("TeutonLib/StationBuildTest").error(result, failure)
            StationBuildVerification.station = null
        }
    }
}
