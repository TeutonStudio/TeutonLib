package de.TeutonStudio.TeutonLib.traintest

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.track.TrackBlock
import com.simibubi.create.content.trains.track.TrackShape
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
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
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.nio.file.Files
import java.util.Properties

/** Explicit save/restart checkpoints, including both naturally empty states. */
object StationPersistenceVerification {
    private fun start(i: Int) = BlockPos(1024 + i * 128, 180, 64)
    private fun station(level: ServerLevel, i: Int) = level.getBlockEntity(start(i).north(3)) as StationBlockEntity
    private fun file(level: ServerLevel) = level.server.getWorldPath(LevelResource.ROOT).resolve("station-persistence.properties")

    private fun place(level: ServerLevel, station: StationBlockEntity, pos: BlockPos) {
        val player = FakePlayerFactory.getMinecraft(level)
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AllBlocks.RAILWAY_CASING.asItem(), 16))
        player.xRot = 30f
        check(station.trackClicked(player, InteractionHand.MAIN_HAND, AllBlocks.TRACK.get(), level.getBlockState(pos), pos)) {
            "Native placement rejected at $pos: assembling=${station.isAssembling}; area=${StationBlockEntity.assemblyAreas.get(level)[station.blockPos]}; direction=${station.assemblyDirection}; station=${station.saveWithoutMetadata(level.registryAccess())}"
        }
    }

    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        val root = Commands.literal("teutonlib_station_persistence_test").requires { it.hasPermission(4) }
        fun command(name: String, action: (ServerLevel) -> String) {
            root.then(Commands.literal(name).executes { ctx ->
                val result = try { action(ctx.source.level) } catch (e: Throwable) { "FAIL ${e.stackTraceToString()}" }
                ctx.source.sendSuccess({ Component.literal(result) }, false); 1
            })
        }
        command("setup") { level ->
            for (i in 0..2) {
                val at = start(i)
                for (x in ((at.x - 16) shr 4)..((at.x + 80) shr 4)) for (z in 3..5) level.setChunkForced(x, z, true)
                for (x in -8..72) {
                    level.setBlockAndUpdate(at.east(x).below(), Blocks.STONE.defaultBlockState())
                    level.setBlockAndUpdate(at.east(x), AllBlocks.TRACK.defaultState.setValue(TrackBlock.SHAPE, TrackShape.XO))
                }
                val pos = at.north(3)
                level.setBlockAndUpdate(pos, AllBlocks.TRACK_STATION.defaultState)
                val station = station(level, i)
                TrackTargetingBehaviour::class.java.getDeclaredField("targetTrack").also { it.isAccessible = true }
                    .set(station.edgePoint, at.subtract(pos))
                TrackTargetingBehaviour::class.java.getDeclaredField("targetDirection").also { it.isAccessible = true }
                    .set(station.edgePoint, Direction.AxisDirection.POSITIVE)
                station.setChanged()
            }
            "PASS setup; wait for native track graph"
        }
        command("checkpoint") { level ->
            val values = Properties()
            for (i in 0..2) {
                val station = station(level, i)
                check(station.enterAssemblyMode(null))
                if (!ServerConfig.hybridTrains()) {
                    check(StationBuildRuntime.bodies(station).isEmpty())
                    place(level, station, start(i).east())
                    check(level.getBlockState(start(i).east().above()).block is com.simibubi.create.content.trains.bogey.AbstractBogeyBlock<*>)
                    check(station.exitAssemblyMode())
                } else {
                    val body = StationBuildRuntime.bodies(station).single()
                    values["body$i"] = body.uniqueId.toString()
                    if (i > 0) place(level, station, start(i).east())
                    if (i == 1) {
                        level.setBlockAndUpdate(body.plot.centerBlock.above(), Blocks.CHEST.defaultBlockState())
                        (level.getBlockEntity(body.plot.centerBlock.above()) as ChestBlockEntity).setItem(0, ItemStack(Items.DIAMOND, 37))
                        values["bearing"] = "true"
                        val bearing = body.plot.centerBlock.offset(3, 2, 0)
                        val facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING
                        level.setBlockAndUpdate(bearing.below(), AllBlocks.CREATIVE_MOTOR.defaultState.setValue(facing, Direction.UP))
                        level.setBlockAndUpdate(bearing, AllBlocks.MECHANICAL_BEARING.defaultState.setValue(facing, Direction.UP))
                        level.setBlockAndUpdate(bearing.above(), Blocks.GLASS.defaultBlockState())
                        (level.getBlockEntity(bearing) as com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity).assemble()
                    }
                    if (i == 2) for (pos in StationBuildRuntime.blocks(body)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
                    if (i != 1) {
                        check(StationBuildRuntime.blocks(body).isEmpty())
                        check(body.selfMassTracker.mass == 0.0 && body.selfMassTracker.centerOfMass == null)
                        check(StationBuildRuntime.dormant(body))
                    }
                    check(level.server.saveEverything(false, true, true))
                }
            }
            Files.newBufferedWriter(file(level)).use { values.store(it, "Disposable test checkpoint") }
            check(level.server.saveEverything(false, true, true))
            "PASS real save: first-empty, occupied-cargo, last-block-removed; enabled=${ServerConfig.hybridTrains()}"
        }
        command("loaded") { level ->
            val values = Properties().also { Files.newBufferedReader(file(level)).use(it::load) }
            if (values.isEmpty()) return@command "PASS disabled native control restart"
            if ((0..2).any { station(level, it).let { station -> station.station == null || station.edgePoint.determineGraphLocation() == null } }) return@command "RUNNING waiting for station track graphs"
            // Do not change anything until all three plots have actually loaded.
            val bodies = (0..2).map { i -> StationBuildRuntime.bodies(station(level, i))
                .singleOrNull { it.uniqueId.toString() == values.getProperty("body$i") }
                ?: return@command "RUNNING waiting for construction $i" }
            if (values.getProperty("bearing") == "true") {
                val loadedBearing = level.getBlockEntity(bodies[1].plot.centerBlock.offset(3, 2, 0)) as
                    com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity
                if (!loadedBearing.isRunning) return@command "RUNNING waiting for saved bearing"
            }
            for ((i, body) in bodies.withIndex()) {
                val station = station(level, i)
                org.slf4j.LoggerFactory.getLogger("StationPersistenceTest").info("Reload case {} UUID {} mass {} dormant {}", i, body.uniqueId, body.selfMassTracker.mass, StationBuildRuntime.dormant(body))
                if (i == 1) {
                    check((level.getBlockEntity(body.plot.centerBlock.above()) as ChestBlockEntity).getItem(0).count == 37)
                } else {
                    check(StationBuildRuntime.blocks(body).isEmpty())
                    check(body.selfMassTracker.mass == 0.0 && body.selfMassTracker.centerOfMass == null)
                    check(StationBuildRuntime.dormant(body))
                    org.slf4j.LoggerFactory.getLogger("StationPersistenceTest").info("First placement after reload {}", i)
                    place(level, station, start(i).east())
                    org.slf4j.LoggerFactory.getLogger("StationPersistenceTest").info("Placed after reload {}", i)
                    check(body.selfMassTracker.centerOfMass != null)
                    check(!StationBuildRuntime.dormant(body))
                }
                check(station.exitAssemblyMode())
                check(StationBuildRuntime.bodies(station).isEmpty())
                if (i == 1) check((level.getBlockEntity(start(i).east().above(2)) as ChestBlockEntity).getItem(0).count == 37)
            }
            check(level.server.saveEverything(false, true, true))
            "PASS restart: same UUIDs, genuine zero mass/null centres, cargo, first placement wakes, cancel and save; enabled=${ServerConfig.hybridTrains()}"
        }
        command("returned") { level ->
            val values = Properties().also { Files.newBufferedReader(file(level)).use(it::load) }
            if (values.isEmpty()) return@command "PASS disabled world has no hybrid machinery"
            val bearing = level.getBlockEntity(start(1).east().above().offset(3, 2, 0)) as
                com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity
            if (!bearing.isRunning) return@command "RUNNING waiting for returned world bearing"
            check(bearing.movedContraption!!.contraption.blocks.values.any { it.state().`is`(Blocks.GLASS) })
            check((level.getBlockEntity(start(1).east().above(2)) as ChestBlockEntity).getItem(0).count == 37)
            check(level.server.saveEverything(false, true, true))
            "PASS cancellation returned and resumed running bearing, payload and cargo in ordinary world"
        }
        event.dispatcher.register(root)
    }
}
