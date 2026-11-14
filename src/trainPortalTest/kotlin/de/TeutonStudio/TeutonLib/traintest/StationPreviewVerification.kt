package de.TeutonStudio.TeutonLib.traintest

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.simibubi.create.AllBlocks
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock
import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.track.TrackBlock
import com.simibubi.create.content.trains.track.TrackShape
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent

object StationPreviewVerification {
    val directions = listOf(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH)
    fun start(i: Int) = BlockPos(i * 192, 180, i * 128)
    fun stationPos(i: Int) = start(i).relative(directions[i].clockWise, 3)
    private fun station(level: ServerLevel, i: Int) = level.getBlockEntity(stationPos(i)) as StationBlockEntity

    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        val root = Commands.literal("teutonlib_station_preview_test").requires { it.hasPermission(4) }
        fun action(name: String, run: (ServerLevel, Int) -> String) {
            root.then(Commands.literal(name).then(Commands.argument("case", IntegerArgumentType.integer(0, 3)).executes { ctx ->
                val result = try { run(ctx.source.level, IntegerArgumentType.getInteger(ctx, "case")) }
                    catch (e: Throwable) { "FAIL ${e.stackTraceToString()}" }
                ctx.source.sendSuccess({ Component.literal(result) }, false); 1
            }))
        }
        action("setup") { level, i ->
            val at = start(i); val direction = directions[i]
            for (x in ((at.x - 80) shr 4)..((at.x + 80) shr 4)) for (z in ((at.z - 80) shr 4)..((at.z + 80) shr 4)) level.setChunkForced(x, z, true)
            // A real walkable test floor, outside the Sable construction plots.
            for (x in -80..80) for (z in -80..80)
                level.setBlock(at.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2)
            for (offset in -8..64) {
                val pos = at.relative(direction, offset)
                level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState())
                level.setBlockAndUpdate(pos, AllBlocks.TRACK.defaultState.setValue(TrackBlock.SHAPE,
                    if (direction.axis == Direction.Axis.X) TrackShape.XO else TrackShape.ZO))
            }
            val pos = stationPos(i)
            level.setBlockAndUpdate(pos, AllBlocks.TRACK_STATION.defaultState)
            val station = station(level, i)
            TrackTargetingBehaviour::class.java.getDeclaredField("targetTrack").also { it.isAccessible = true }.set(station.edgePoint, at.subtract(pos))
            TrackTargetingBehaviour::class.java.getDeclaredField("targetDirection").also { it.isAccessible = true }
                .set(station.edgePoint, direction.axisDirection)
            station.notifyUpdate()
            "PASS setup $i"
        }
        action("enter") { level, i -> check(station(level, i).enterAssemblyMode(null)); "PASS enter $i" }
        action("status") { level, i ->
            val station = station(level, i)
            station.refreshAssemblyInfo()
            val bogeys = StationBuildRuntime.bodies(station).flatMap { body -> StationBuildRuntime.blocks(body).mapNotNull { pos ->
                val state = level.getBlockState(pos)
                if (state.block !is AbstractBogeyBlock<*>) null else {
                    val world = StationBuildRuntime.toWorld(body, pos)
                    val delta = world.subtract(start(i)); val direction = directions[i]
                    mapOf("offset" to delta.x * direction.stepX + delta.z * direction.stepZ - 1,
                        "world" to listOf(world.x, world.y, world.z), "plot" to listOf(pos.x,pos.y,pos.z),
                        "state" to state.toString(),
                        "canBeUpsideDown" to (state.block as AbstractBogeyBlock<*>).canBeUpsideDown(),
                        "upsideDown" to (state.block as AbstractBogeyBlock<*>).isUpsideDown(state))
                }
            } }
            com.google.gson.Gson().toJson(mapOf("case" to i, "bogeys" to bogeys))
        }
        action("remove") { level, i ->
            for (body in StationBuildRuntime.bodies(station(level, i))) for (pos in StationBuildRuntime.blocks(body))
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
            "PASS removed $i"
        }
        action("cancel") { level, i -> check(station(level, i).exitAssemblyMode()); "PASS cancelled $i" }
        root.then(Commands.literal("equip").executes { ctx ->
            for (player in ctx.source.server.playerList.players) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack(AllBlocks.RAILWAY_CASING.asItem(), 64))
                player.abilities.mayfly = true
                player.abilities.flying = true
                player.fallDistance = 0f
                player.onUpdateAbilities()
            }
            ctx.source.sendSuccess({ Component.literal("PASS equipped") }, false); 1
        })
        event.dispatcher.register(root)
    }
}
