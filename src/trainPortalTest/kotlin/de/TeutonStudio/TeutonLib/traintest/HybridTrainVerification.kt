package de.TeutonStudio.TeutonLib.traintest

import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity
import com.happysg.radar.block.radar.bearing.RadarContraption
import com.happysg.radar.registry.ModBlocks
import com.simibubi.create.AllBlocks
import com.simibubi.create.Create
import com.simibubi.create.content.contraptions.Contraption
import com.simibubi.create.content.contraptions.ControlledContraptionEntity
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridCarriageContraption
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridRadarData
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.Mode
import de.TeutonStudio.TeutonLib.integration.train.CtfPortalAccess
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalTransfers
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.FurnaceBlockEntity
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.Properties
import java.util.UUID

/** Exercises actual block/chunk guards, machine ticks, Radar's real controller and native rail portal handoffs. */
object HybridTrainVerification {
    private val log = LoggerFactory.getLogger("TeutonLib/HybridTrainTest")
    private var fixture: TrainTraversalVerification.HybridFixture? = null
    private var source: ServerLevel? = null
    private var phase = 0
    private var ticks = 0
    private var result = "IDLE"
    private var rotorId: UUID? = null
    private var oldAngle = 0f
    private val bearingOffset = BlockPos(5, 7, 0)
    private val furnaceOffset = BlockPos(2, 7, 0)
    private val lampOffset = BlockPos(3, 7, 0)

    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("teutonlib_hybrid_train_test").requires { it.hasPermission(4) }
            .then(Commands.literal("start").executes { ctx ->
                source = ctx.source.level
                fixture = TrainTraversalVerification.hybridFixture(ctx.source.level)
                phase = 0; ticks = 0; result = "RUNNING automatic standard conversion"
                ctx.source.sendSuccess({ Component.literal(result) }, false); 1
            })
            .then(Commands.literal("status").executes { ctx ->
                ctx.source.sendSuccess({ Component.literal(result) }, false); 1
            })
            .then(Commands.literal("loaded").executes { ctx ->
                val message = try { loaded(ctx.source.server) } catch (failure: Throwable) { "FAIL restart $failure" }
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }))
    }

    private fun current(server: MinecraftServer): Pair<SubLevelLinks.Link, ServerSubLevel> {
        val id = checkNotNull(fixture).subId
        val link = checkNotNull(SubLevelLinks.linkForSubLevel(id))
        val level = checkNotNull(server.getLevel(link.dimension))
        return link to (SubLevelContainer.getContainer(level)!!.getSubLevel(id) as ServerSubLevel)
    }

    private fun bearing(link: SubLevelLinks.Link, body: ServerSubLevel) =
        body.level.getBlockEntity(link.plotOrigin.offset(bearingOffset)) as RadarBearingBlockEntity

    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        val active = fixture ?: return
        try {
            ticks++
            check(ticks < 600) { "Hybrid test timed out in phase $phase: $result" }
            check(!TrainTraversalVerification.status().startsWith("FAIL")) { TrainTraversalVerification.status() }
            val (link, body) = current(event.server)
            val level = body.level
            when (phase) {
                0 -> {
                    val rear = active.train.carriages.last().anyAvailableEntity() ?: return
                    val rearLink = SubLevelLinks.linkFor(rear.uuid) ?: return
                    check(rear.contraption is HybridCarriageContraption)
                    check(HybridTrainRuntime.mode(checkNotNull(HybridTrainRuntime.sub(level, rearLink))) == Mode.DRIVING)
                    check(HybridTrainRuntime.changeMode(level, active.train, Mode.EDITING) == 2)
                    val point = active.train.carriages.first().leadingPoint.position
                    check(active.train.carriages.first().travel(level, active.train.graph, .25, null, null, 3) == 0.0)
                    check(active.train.carriages.first().leadingPoint.position == point)
                    for (offset in listOf(furnaceOffset, lampOffset, bearingOffset)) {
                        // Attach machines to the existing y=2 deck, so the EDITING
                        // checkpoint is a genuine connected build as well.
                        for (y in 3..6) check(level.setBlockAndUpdate(link.plotOrigin.offset(offset.x, y, offset.z), Blocks.STONE.defaultBlockState()))
                    }
                    check(level.setBlockAndUpdate(link.plotOrigin.offset(furnaceOffset), Blocks.FURNACE.defaultBlockState()))
                    val furnace = level.getBlockEntity(link.plotOrigin.offset(furnaceOffset)) as FurnaceBlockEntity
                    furnace.setItem(0, ItemStack(Items.SAND, 2)); furnace.setItem(1, ItemStack(Items.COAL, 2))
                    check(level.setBlockAndUpdate(link.plotOrigin.offset(lampOffset), Blocks.REDSTONE_LAMP.defaultBlockState()
                        .setValue(BlockStateProperties.LIT, true)))
                    level.scheduleTick(link.plotOrigin.offset(lampOffset), Blocks.REDSTONE_LAMP, 2)
                    check(level.setBlockAndUpdate(link.plotOrigin.offset(bearingOffset).below(), AllBlocks.CREATIVE_MOTOR.defaultState
                        .setValue(BlockStateProperties.FACING, Direction.UP)))
                    check(level.setBlockAndUpdate(link.plotOrigin.offset(bearingOffset), ModBlocks.RADAR_BEARING_BLOCK.defaultState))
                    check(level.setBlockAndUpdate(link.plotOrigin.offset(bearingOffset).above(), ModBlocks.RADAR_RECEIVER_BLOCK.defaultState
                        .setValue(BlockStateProperties.FACING, Direction.NORTH)))
                    check(level.setBlockAndUpdate(link.plotOrigin.offset(bearingOffset).above(2), ModBlocks.CREATIVE_RADAR_PLATE_BLOCK.defaultState))
                    HybridEquipmentVerification.setup(level, link.plotOrigin)
                    HybridTrainRuntime.changeMode(level, active.train, Mode.DRIVING)
                    check(level.getBlockEntity(link.plotOrigin.offset(furnaceOffset)) === furnace) { "Mode switch replaced the live machine" }
                    val before = level.getBlockState(link.plotOrigin)
                    check(!level.setBlockAndUpdate(link.plotOrigin, Blocks.AIR.defaultBlockState()))
                    check(level.getChunkAt(link.plotOrigin).setBlockState(link.plotOrigin, Blocks.DIRT.defaultBlockState(), false) == null)
                    check(!level.destroyBlock(link.plotOrigin, true))
                    check(level.getBlockState(link.plotOrigin) == before)
                    val owner = CtfPortalAccess.entity(link) as CarriageContraptionEntity
                    val originalSnapshot = owner.contraption.blocks[BlockPos.ZERO]
                    owner.setBlock(BlockPos.ZERO, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                        BlockPos.ZERO, Blocks.DIRT.defaultBlockState(), null))
                    check(owner.contraption.blocks[BlockPos.ZERO] == originalSnapshot) { "Create's block snapshot bypassed the structural lock" }
                    active.train.speed = .2
                    check(runCatching { HybridTrainRuntime.changeMode(level, active.train, Mode.EDITING) }.isFailure)
                    active.train.speed = 0.0
                    check(HybridTrainRuntime.mode(body) == Mode.DRIVING)
                    bearing(link, body).assemble()
                    val radar = bearing(link, body).contraption.orElseThrow()
                    check(radar is RadarContraption && radar.hasReceiver() && radar.dishCount > 0)
                    val saved = radar.writeNBT(level.registryAccess(), false)
                    val read = Contraption.fromNBT(level, saved, false) as RadarContraption
                    check(read.dishCount == radar.dishCount && read.receiverFacing == radar.receiverFacing && read.isCreative)
                    rotorId = checkNotNull(bearing(link, body).movedContraption).uuid
                    oldAngle = bearing(link, body).angle
                    HybridEquipmentVerification.start(level, link.plotOrigin)
                    phase = 1; result = "RUNNING live machinery and nested radar"
                }
                1 -> if (ticks > 30) {
                    HybridEquipmentVerification.verify(level, link.plotOrigin, fire = true)
                    val radar = bearing(link, body)
                    check(radar.angle != oldAngle) { "Radar did not rotate on the locked train" }
                    check(!level.getBlockState(link.plotOrigin.offset(lampOffset)).getValue(BlockStateProperties.LIT)) {
                        "Scheduled redstone lamp tick did not run"
                    }
                    check(level.getBlockState(link.plotOrigin.offset(furnaceOffset)).getValue(BlockStateProperties.LIT)) { "Furnace did not tick" }
                    // A running child can stop and restart without opening the parent's build lock.
                    radar.disassemble()
                    check(level.getBlockState(link.plotOrigin.offset(bearingOffset).above()).block == ModBlocks.RADAR_RECEIVER_BLOCK.get())
                    check(!level.destroyBlock(link.plotOrigin.offset(bearingOffset).above(), true))
                    radar.assemble()
                    rotorId = checkNotNull(radar.movedContraption).uuid
                    oldAngle = radar.angle
                    TrainTraversalVerification.visualStep(35.0)
                    phase = 2; result = "RUNNING native portal outbound with active radar"
                }
                2 -> if (level === active.destination && TrainTraversalVerification.status().startsWith("READY")) {
                    verifyRotor(link, body)
                    HybridEquipmentVerification.verify(level, link.plotOrigin, fire = true)
                    check(HybridTrainRuntime.mode(body) == Mode.DRIVING)
                    check(event.server.allLevels.sumOf { home -> SubLevelContainer.getContainer(home)!!.allSubLevels.count { it.uniqueId == body.uniqueId } } == 1)
                    val invalid = CarriageContraptionEntity(checkNotNull(CtfPortalAccess.entity(link)).type, checkNotNull(source))
                    check(!TrainPortalTransfers.handoff(link, body, invalid, active.portals.last()))
                    val (restoredLink, restored) = current(event.server)
                    check(HybridTrainRuntime.mode(restored) == Mode.DRIVING)
                    phase = 21; oldAngle = ticks.toFloat(); result = "RUNNING rollback machine initialization"
                }
                21 -> if (ticks > oldAngle.toInt() + 20) {
                    verifyRotor(link, body)
                    HybridEquipmentVerification.verify(level, link.plotOrigin)
                    TrainTraversalVerification.visualStep(-35.0)
                    phase = 3; result = "RUNNING native portal return after rollback"
                }
                3 -> if (level === source && TrainTraversalVerification.status().startsWith("READY")) {
                    verifyRotor(link, body)
                    HybridEquipmentVerification.verify(level, link.plotOrigin, fire = true)
                    // Clear both the physical hull and the rear carriage's portal side before editing.
                    TrainTraversalVerification.visualStep(-6.0)
                    phase = 4; result = "RUNNING checking machine progress after transfers"
                }
                4 -> if (ticks > 270 && TrainTraversalVerification.status().startsWith("READY")) {
                    val furnace = level.getBlockEntity(link.plotOrigin.offset(furnaceOffset)) as FurnaceBlockEntity
                    check(furnace.getItem(2).`is`(Items.GLASS)) {
                        "Furnace lost processing progress across portal transfers: ${furnace.saveWithFullMetadata(level.registryAccess())}"
                    }
                    HybridTrainRuntime.changeMode(level, active.train, Mode.EDITING)
                    val values = Properties().also {
                        it["train"] = active.train.id.toString(); it["sublevel"] = body.uniqueId.toString()
                        it["rotor"] = rotorId.toString()
                        HybridEquipmentVerification.save(it)
                    }
                    Files.newBufferedWriter(event.server.getWorldPath(LevelResource.ROOT).resolve("hybrid-train-checkpoint.properties")).use {
                        values.store(it, "Disposable hybrid train verification")
                    }
                    result = "PASS hybrid standard; 2 live carriages; fixed blocks; editable Sable mode; parked editing; " +
                        "machine+scheduled ticks; nested radar assembly+rotation+placement; cross portal outbound+return; " +
                        "rollback; radar resumed without duplicate rotors; Big Cannons+autocannon ammunition+real shots; " +
                        "mechanical bearing payload; swivel payload+inventory; physical hose head+native SIMULATE/EXECUTE fluids; smelting progress; checkpoint EDITING"
                    fixture = null
                }
            }
        } catch (failure: Throwable) {
            result = "FAIL hybrid phase=$phase ticks=$ticks $failure"
            log.error(result, failure); fixture = null
        }
    }

    private fun verifyRotor(link: SubLevelLinks.Link, body: ServerSubLevel) {
        val radar = bearing(link, body)
        val entity = radar.movedContraption
        check(entity != null && entity.isAlive && entity.contraption is RadarContraption) {
            "Portal did not resume the typed radar: running=${radar.isRunning}, dish=$entity, type=${entity?.contraption?.javaClass}, " +
                "exception=${radar.lastAssemblyException?.message}, speed=${radar.speed}, " +
                "receiver=${body.level.getBlockState(link.plotOrigin.offset(bearingOffset).above())}, " +
                "plate=${body.level.getBlockState(link.plotOrigin.offset(bearingOffset).above(2))}"
        }
        check(radar.movedContraption === entity && radar.contraption.isPresent) { "Radar controller did not bind to its transferred dish" }
        // IP 2.6.4 deliberately places bearing assemblies back before transferring
        // the plot, then reassembles them. Their entity UUID can therefore change.
        if (rotorId != entity.uuid) {
            check(body.level.server.allLevels.none { it.getEntity(checkNotNull(rotorId))?.isAlive == true }) {
                "Portal left the old rotor alive as well as its reassembled replacement"
            }
        }
        rotorId = entity.uuid
        check((entity.contraption as HybridRadarData).`teutonlib$getDishCount`() > 0)
    }

    private fun loaded(server: MinecraftServer): String {
        val values = Properties().also { properties -> Files.newBufferedReader(
            server.getWorldPath(LevelResource.ROOT).resolve("hybrid-train-checkpoint.properties")).use(properties::load) }
        val train = Create.RAILWAYS.trains[UUID.fromString(values.getProperty("train"))] ?: return "RUNNING loading train"
        val link = SubLevelLinks.linkForSubLevel(UUID.fromString(values.getProperty("sublevel"))) ?: return "RUNNING relinking simulation"
        val level = checkNotNull(server.getLevel(link.dimension))
        val body = HybridTrainRuntime.sub(level, link) ?: return "RUNNING loading simulation"
        check(HybridTrainRuntime.mode(body) == Mode.EDITING)
        check(train.carriages.all { it.anyAvailableEntity()?.contraption is HybridCarriageContraption })
        check(level.getBlockEntity(link.plotOrigin.offset(furnaceOffset)) is FurnaceBlockEntity) { "Restart lost the machine deck" }
        rotorId = UUID.fromString(values.getProperty("rotor"))
        if (level.getEntity(checkNotNull(rotorId)) == null) return "RUNNING loading rotor"
        verifyRotor(link, body)
        check(rotorId == UUID.fromString(values.getProperty("rotor"))) { "Restart replaced the rotor UUID" }
        check((level.getBlockEntity(link.plotOrigin.offset(furnaceOffset)) as FurnaceBlockEntity).getItem(2).`is`(Items.GLASS))
        HybridTrainRuntime.changeMode(level, train, Mode.DRIVING)
        HybridEquipmentVerification.load(values)
        HybridEquipmentVerification.verify(level, link.plotOrigin)
        check(!level.destroyBlock(link.plotOrigin, true))
        return "PASS hybrid restart: special carriage type, saved EDITING mode, live radar UUID+type+controller, cargo+machine state; mechanical+swivel payloads, CBC ammunition, physical hose UUID+native fluids; resumed DRIVING"
    }
}
