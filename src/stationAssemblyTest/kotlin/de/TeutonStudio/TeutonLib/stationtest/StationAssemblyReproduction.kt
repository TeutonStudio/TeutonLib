package de.TeutonStudio.TeutonLib.stationtest

import com.simibubi.create.AllBlocks
import com.simibubi.create.Create
import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.station.AssemblyScreen
import com.simibubi.create.content.trains.track.TrackBlock
import com.simibubi.create.content.trains.track.TrackShape
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour
import com.simibubi.create.foundation.gui.widget.IconButton
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import org.slf4j.LoggerFactory
import java.nio.file.Files

/** Opt-in integrated-client reproduction with the DEFAULT runtime dependencies.
 * Launch only in an isolated gameDirectory with -Dteutonlib.stationAssemblyRepro=true.
 * It opens Create's real assembly screen and clicks its real assembly button. */
@Mod("teutonlib_station_assembly_test")
class StationAssemblyReproduction {
    init {
        check(java.lang.Boolean.getBoolean("teutonlib.stationAssemblyRepro"))
        NeoForge.EVENT_BUS.register(this)
    }
    private val log = LoggerFactory.getLogger("TeutonLib/StationAssemblyRepro")
    private val start = BlockPos(0, 180, 0)
    private val stationPos = start.north(3)
    private var station: StationBlockEntity? = null
    @Volatile private var phase = 0
    @Volatile private var ticks = 0
    private var clientTicks = 0
    private var originalBody: java.util.UUID? = null
    private fun output(name: String, text: String) {
        Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve(name), text)
    }

    @SubscribeEvent fun server(event: ServerTickEvent.Post) {
        val player = event.server.playerList.players.firstOrNull() ?: return
        val level = event.server.overworld()
        ticks++
        when (phase) {
            0 -> {
                check(event.server.isSingleplayer) { "Reproduction requires disposable integrated world" }
                player.connection.teleport(4.0, 183.0, -5.0, 0f, 20f)
                for (x in -1..2) for (z in -1..0) level.setChunkForced(x, z, true)
                for (x in 0..9) for (z in -6..-2)
                    level.setBlockAndUpdate(BlockPos(x, 181, z), Blocks.STONE.defaultBlockState())
                for (x in -8..40) {
                    level.setBlockAndUpdate(start.east(x).below(), Blocks.STONE.defaultBlockState())
                    level.setBlockAndUpdate(start.east(x), AllBlocks.TRACK.defaultState.setValue(TrackBlock.SHAPE, TrackShape.XO))
                }
                level.setBlockAndUpdate(stationPos, AllBlocks.TRACK_STATION.defaultState)
                station = level.getBlockEntity(stationPos) as StationBlockEntity
                TrackTargetingBehaviour::class.java.getDeclaredField("targetTrack").also { it.isAccessible = true }
                    .set(station!!.edgePoint, start.subtract(stationPos))
                TrackTargetingBehaviour::class.java.getDeclaredField("targetDirection").also { it.isAccessible = true }
                    .set(station!!.edgePoint, Direction.AxisDirection.POSITIVE)
                station!!.setChanged()
                phase = 1; ticks = 0
            }
            1 -> if (ticks > 40 && station!!.station != null) {
                check(station!!.enterAssemblyMode(player))
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AllBlocks.RAILWAY_CASING.asItem(), 16))
                for (offset in listOf(1, 7)) {
                    val pos = start.east(offset)
                    check(station!!.trackClicked(player, InteractionHand.MAIN_HAND, AllBlocks.TRACK.get(), level.getBlockState(pos), pos))
                }
                val body = StationBuildRuntime.bodies(station!!).single()
                originalBody = body.uniqueId
                for (offset in 1..7) {
                    val pos = StationBuildRuntime.toPlot(body, start.east(offset).above(2))
                    level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState())
                }
                log.info("REPRO ready: two bogeys, seven grass blocks, no controls; hybrid={}, portal={}",
                    de.TeutonStudio.TeutonLib.integration.HybridTrainCompatibility.active(),
                    de.TeutonStudio.TeutonLib.integration.TrainPortalCompatibility.active())
                station!!.notifyUpdate()
                phase = 2; ticks = 0
            }
            3 -> if (ticks > 100) {
                val tag = station!!.saveWithoutMetadata(level.registryAccess())
                check(tag.getCompound("LastException").getString("Component").contains("train_assembly.no_controls")) {
                    "Expected native no_controls rejection, got $tag"
                }
                check(Create.RAILWAYS.trains.isEmpty())
                val body = StationBuildRuntime.bodies(station!!).single()
                check(body.uniqueId == originalBody && !StationBuildRuntime.dormant(body))
                val blocks = StationBuildRuntime.blocks(body)
                check(blocks.size == 9 && blocks.count { level.getBlockState(it).`is`(Blocks.GRASS_BLOCK) } == 7)
                log.info("REPRO PASS: native no_controls rejection, same construction UUID, two bogeys and seven grass blocks restored")
                output("assembly-rejection.txt", "PASS\n$tag\n")
                level.setBlockAndUpdate(body.plot.centerBlock.above(2), AllBlocks.TRAIN_CONTROLS.defaultState
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST))
                phase = 4
            }
            5 -> if (ticks > 100) {
                val train = Create.RAILWAYS.trains.values.single()
                check(train.carriages.size == 1)
                val link = createtrainfunctionality.SubLevelLinks.links().single { link ->
                    de.TeutonStudio.TeutonLib.integration.train.CtfPortalAccess.carriage(link)?.train === train
                }
                val body = checkNotNull(de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.sub(level, link))
                check(StationBuildRuntime.blocks(body).count { level.getBlockState(it).`is`(Blocks.GRASS_BLOCK) } == 7)
                log.info("REPRO PASS: native assembly with controls created one carriage, CTF simulation and seven grass blocks")
                output("assembly-result.txt", "PASS rejection and successful assembly\ntrain=${train.id}\nsublevel=${body.uniqueId}\n")
                phase = 6
            }
        }
    }

    @SubscribeEvent fun client(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        if (phase == 2 || phase == 4) {
            val be = mc.level?.getBlockEntity(stationPos) as? StationBlockEntity ?: return
            val global = be.station ?: return
            if (mc.screen !is AssemblyScreen) { mc.setScreen(AssemblyScreen(be, global)); clientTicks = 0 }
            if (++clientTicks < 60) return
            val withControls = phase == 4
            Screenshot.grab(mc.gameDirectory, if (withControls) "assembly-with-controls.png" else "assembly-before.png", mc.mainRenderTarget) {}
            val button = AssemblyScreen::class.java.getDeclaredField("toggleAssemblyButton").also { it.isAccessible = true }
                .get(mc.screen) as IconButton
            check(button.active) { "Native assemble button disabled" }
            log.info("REPRO clicking native AssemblyScreen assembly button")
            phase = if (withControls) 5 else 3; ticks = 0; clientTicks = 0
            button.onClick(button.x + 1.0, button.y + 1.0)
        } else if (phase == 6) {
            Screenshot.grab(mc.gameDirectory, "assembly-after.png", mc.mainRenderTarget) {}
            phase = 7
            mc.stop()
        }
    }
}
