package de.TeutonStudio.TeutonLib.traintest

import com.simibubi.create.content.trains.entity.CarriageContraption
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import com.simibubi.create.AllBlocks
import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.CarriageBogey
import com.simibubi.create.content.trains.entity.Train
import com.simibubi.create.content.trains.entity.TravellingPoint
import com.simibubi.create.content.trains.entity.TravellingPoint.IPortalListener
import com.simibubi.create.content.trains.graph.TrackGraph
import com.simibubi.create.content.trains.graph.TrackNode
import com.simibubi.create.content.trains.graph.TrackNodeLocation
import com.simibubi.create.content.trains.graph.TrackEdge
import com.simibubi.create.content.trains.track.TrackMaterial
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.TrainPortalCompatibility
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalGuard
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.joml.Quaterniond
import org.slf4j.LoggerFactory
import qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge
import qouteall.imm_ptl.core.compat.create_integration.IPCreatePortalEdges
import java.util.UUID
import com.mojang.brigadier.arguments.DoubleArgumentType

/** Real CTF conversion, Sable blocks/inventory and transformed IP transfers in a disposable world. */
@Mod("teutonlib_train_portal_test")
class TrainPortalVerification {
    init {
        NeoForge.EVENT_BUS.register(this)
        NeoForge.EVENT_BUS.register(StationBuildVerification)
        NeoForge.EVENT_BUS.register(StationPersistenceVerification)
        NeoForge.EVENT_BUS.register(StationPreviewVerification)
        if (net.neoforged.fml.ModList.get().isLoaded("create_radar")) {
            NeoForge.EVENT_BUS.register(HybridTrainVerification)
            NeoForge.EVENT_BUS.register(TrainDemo)
        }
    }

    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("teutonlib_train_riders_test").requires { it.hasPermission(4) }
            .executes { context ->
                val result = TrainRiderVerification.verify(context.source.level)
                context.source.sendSuccess({ Component.literal(result) }, false); 1
            })
        event.dispatcher.register(Commands.literal("teutonlib_train_visual").requires { it.hasPermission(4) }
            .then(Commands.literal("hybrid").then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes { ctx ->
                val enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "enabled")
                de.TeutonStudio.TeutonLib.ServerConfig.HYBRID_TRAINS.set(enabled)
                ctx.source.sendSuccess({ Component.literal("hybrid=$enabled") }, false); 1
            }))
            .then(Commands.literal("aether-prepare").executes { ctx ->
                val source = ctx.source.level
                val player = source.server.playerList.players.firstOrNull() ?: FakePlayerFactory.getMinecraft(source)
                val message = NativeAetherPortalVerification.prepare(source, player)
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            })
            .then(Commands.literal("aether-status").executes { ctx ->
                val message = NativeAetherPortalVerification.status(ctx.source.server)
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            })
            .then(Commands.literal("rider").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).executes { ctx ->
                val message = TrainTraversalVerification.startRider(ctx.source.level,
                    net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"))
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }))
            .then(Commands.literal("seated").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).executes { ctx ->
                val message = TrainTraversalVerification.startRider(ctx.source.level,
                    net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"), seated = true)
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }))
            .then(Commands.literal("seat-rollback").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).executes { ctx ->
                val message = TrainTraversalVerification.startSeatedRollback(ctx.source.level,
                    net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"))
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }))
            .then(Commands.literal("start").executes { ctx ->
                val message = TrainTraversalVerification.start(ctx.source.level, false, allBuildable = true, visual = true)
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }).then(Commands.literal("step").then(Commands.argument("distance", DoubleArgumentType.doubleArg(-40.0, 40.0)).executes { ctx ->
                val message = TrainTraversalVerification.visualStep(DoubleArgumentType.getDouble(ctx, "distance"))
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            })).then(Commands.literal("status").executes { ctx ->
                ctx.source.sendSuccess({ Component.literal(TrainTraversalVerification.status()) }, false); 1
            }).then(Commands.literal("end").executes { ctx ->
                ctx.source.sendSuccess({ Component.literal(TrainTraversalVerification.endVisual()) }, false); 1
            }))
        event.dispatcher.register(Commands.literal("teutonlib_train_traversal").requires { it.hasPermission(4) }
            .then(Commands.literal("cross").executes { ctx ->
                val message = TrainTraversalVerification.start(ctx.source.level, false)
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }).then(Commands.literal("same").executes { ctx ->
                val message = TrainTraversalVerification.start(ctx.source.level, true)
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }).then(Commands.literal("status").executes { ctx ->
                ctx.source.sendSuccess({ Component.literal(TrainTraversalVerification.status()) }, false); 1
            }).then(Commands.literal("decouple").executes { ctx ->
                val message = TrainTraversalVerification.decouple(ctx.source.level)
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }).then(Commands.literal("checkpoint").executes { ctx ->
                val message = TrainTraversalVerification.start(ctx.source.level, false, true)
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }).then(Commands.literal("multi").executes { ctx ->
                val message = TrainTraversalVerification.start(ctx.source.level, false, allBuildable = true)
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }).then(Commands.literal("loaded").executes { ctx ->
                val message = TrainTraversalVerification.loaded(ctx.source.server)
                ctx.source.sendSuccess({ Component.literal(message) }, false); 1
            }))
        event.dispatcher.register(Commands.literal("teutonlib_train_portal_test").requires { it.hasPermission(4) }
            .executes { context ->
                try {
                    val result = verify(context.source.level)
                    context.source.sendSuccess({ Component.literal("Train portals PASS $result") }, false)
                    1
                } catch (failure: Throwable) {
                    LoggerFactory.getLogger("TeutonLib/TrainPortalTest").error("Train portals FAIL", failure)
                    context.source.sendFailure(Component.literal("Train portals FAIL $failure"))
                    0
                }
            })
    }

    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        TrainTraversalVerification.tick(event.server)
    }

    private fun verify(level: ServerLevel): String {
        check(TrainPortalCompatibility.active())
        val destination = TrainPortalTestDestination.level(level)
        val at = BlockPos(32, 160, 32)
        level.setChunkForced(2, 2, true)
        val contraption = CarriageContraption(Direction.NORTH)
        contraption.anchor = at
        contraption.bounds = AABB(0.0, 0.0, 0.0, 1.0, 2.0, 1.0)
        contraption.blocks[BlockPos.ZERO] = StructureBlockInfo(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)
        contraption.blocks[BlockPos(0, 1, 0)] = StructureBlockInfo(BlockPos(0, 1, 0), Blocks.CHEST.defaultBlockState(), null)
        val entity = CarriageContraptionEntity.create(level, contraption)
        entity.setPos(32.0, 160.0, 32.0)
        val carriage = carriage()
        carriage.storage.initialize()
        Train(UUID.randomUUID(), null, TrackGraph(), listOf(carriage), emptyList(), false, 0)
        entity.setCarriage(carriage)
        val link = checkNotNull(SubLevelLinks.convert(level, entity))
        val sub = checkNotNull(SubLevelContainer.getContainer(level)).getSubLevel(link.subLevelId) as ServerSubLevel
        val chestPos = link.plotOrigin.above()
        val chest = level.getBlockEntity(chestPos) as ChestBlockEntity
        chest.setItem(0, ItemStack(Items.DIAMOND, 17))
        check(SubLevelLinks.linkForSubLevel(sub.uniqueId) != null)
        assertUnavailablePortalStop(level, carriage)
        val itemsBefore = level.getEntitiesOfClass(ItemEntity::class.java, AABB(at).inflate(64.0)).size
        assertRefused(level, destination, sub)
        assertRefused(level, level, sub) // same-dimension portals also reallocate plots
        check(SubLevelLinks.linkForSubLevel(sub.uniqueId) === link)
        assertScannersSkip(level, sub)
        check(level.getBlockState(link.plotOrigin).`is`(Blocks.STONE))
        check((level.getBlockEntity(chestPos) as ChestBlockEntity).getItem(0).count == 17)
        check(level.getEntitiesOfClass(ItemEntity::class.java, AABB(at).inflate(64.0)).size == itemsBefore)

        // Simulate unloaded carriage / freshly read saved ownership, with no live link or OURS cache.
        SubLevelLinks.forgetEverything()
        SubLevelLinks::class.java.getDeclaredMethod("forgetOurs").also { it.isAccessible = true }.invoke(null)
        check(SubLevelLinks.linkForSubLevel(sub.uniqueId) == null)
        check(!SubLevelLinks.isOursEvenIfOrphaned(sub.uniqueId))
        check(SubLevelLinks.isOursEvenIfOrphaned(level, sub.uniqueId))
        assertRefused(level, destination, sub)
        assertScannersSkip(level, sub)
        assertUnavailablePortalStop(level, carriage)

        // Positive control: an ordinary ship must still really move, carrying its inventory.
        val plainPos = at.east(4)
        level.setBlockAndUpdate(plainPos, Blocks.STONE.defaultBlockState())
        level.setBlockAndUpdate(plainPos.above(), Blocks.CHEST.defaultBlockState())
        (level.getBlockEntity(plainPos.above()) as ChestBlockEntity).setItem(0, ItemStack(Items.EMERALD, 11))
        val plain = SubLevelAssemblyHelper.assembleBlocks(level, plainPos, listOf(plainPos, plainPos.above()),
            BoundingBox3i(plainPos, plainPos.above()))
        check(!TrainPortalGuard.owns(level, plain))
        check(IPSableBridge.whyNotMovable(plain, level, destination) == null)
        val moved = checkNotNull(IPSableBridge.moveThroughPortal(plain, level, destination, Vec3(32.0, 100.0, 32.0), null))
        check(checkNotNull(SubLevelContainer.getContainer(level)).getSubLevel(plain.uniqueId) == null)
        check(moved.sub().uniqueId == plain.uniqueId)
        check(moved.sub().level === destination)
        val arrivedChest = destination.getBlockEntity(moved.sub().plot.centerBlock.above()) as ChestBlockEntity
        check(arrivedChest.getItem(0).`is`(Items.EMERALD) && arrivedChest.getItem(0).count == 11)
        check(!sub.isRemoved && checkNotNull(SubLevelContainer.getContainer(level)).getSubLevel(sub.uniqueId) === sub)
        check((level.getBlockEntity(chestPos) as ChestBlockEntity).getItem(0).count == 17)
        return "missing-portal veto (both directions, cross+same dimension); active+saved ownership; " +
            "seamless+fallback scans; cross+same dimension veto; " +
            "blocks+cargo retained; ordinary ship transferred with cargo; " +
            "destination=${destination.dimension().location()} fixture=transfer-contract"
    }

    private fun carriage() = Carriage(CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, CompoundTag()), null, 0)

    private fun assertUnavailablePortalStop(level: ServerLevel, protected: Carriage) {
        // Retrieve the handler from the actually transformed Create class, so an unapplied
        // ModifyArg or a bad captured Level parameter cannot pass this fixture.
        val injection = Carriage::class.java.declaredMethods.single { it.name.contains("teutonlib\$guardTrackPortal") }
            .also { it.isAccessible = true }
        fun listener(carriage: Carriage, delegate: IPortalListener): IPortalListener =
            injection.invoke(carriage, delegate, level) as IPortalListener
        val ordinary = carriage()
        Train(UUID.randomUUID(), null, TrackGraph(), listOf(ordinary), emptyList(), false, 0)
        for (sameDimension in listOf(false, true)) {
            val graph = TrackGraph()
            val nodes = listOf(0.0, 8.0, 100.0, 108.0).mapIndexed { index, x ->
                TrackNode(TrackNodeLocation(x, 160.0, 0.0).`in`(
                    if (index < 2 || sameDimension) level.dimension() else TrainPortalTestDestination.key),
                    TrackGraph.nextNodeId(), Vec3(0.0, 1.0, 0.0)).also(graph::addNode)
            }
            IPCreatePortalEdges.add(nodes[1].location, nodes[2].location)
            for (index in 0..2) {
                val a = nodes[index]
                val b = nodes[index + 1]
                graph.putConnection(a, b, TrackEdge(a, b, null, TrackMaterial.ANDESITE))
                graph.putConnection(b, a, TrackEdge(b, a, null, TrackMaterial.ANDESITE))
            }
            check(graph.getConnectionsFrom(nodes[1]).getValue(nodes[2]).length == 0.0)
            for (forward in listOf(true, false)) {
                fun point(): TravellingPoint {
                    val a = nodes[if (forward) 0 else 2]
                    val b = nodes[if (forward) 1 else 3]
                    return TravellingPoint(a, b, graph.getConnectionsFrom(a).getValue(b), if (forward) 7.5 else 0.5, false)
                }
                var delegated = 0
                val allow = IPortalListener { delegated++; false }
                val blocked = point()
                val moved = blocked.travel(graph, if (forward) 1.0 else -1.0, blocked.random(),
                    blocked.ignoreEdgePoints(), blocked.ignoreTurns(), listener(protected, allow))
                check(blocked.blocked && kotlin.math.abs(moved) == 0.5 && delegated == 0)
                check(blocked.node1 === nodes[if (forward) 0 else 2])
                val plain = point()
                plain.travel(graph, if (forward) 1.0 else -1.0, plain.random(), plain.ignoreEdgePoints(),
                    plain.ignoreTurns(), listener(ordinary, allow))
                check(!plain.blocked && delegated == 1)
                check(plain.node1 === nodes[if (forward) 2 else 0])
                // Existing Create/third-party refusal is retained for ordinary trains too.
                check(listener(ordinary, IPortalListener { true }).test(
                    net.createmod.catnip.data.Couple.create(nodes[1].location, nodes[2].location)))
            }
        }
    }

    private fun assertRefused(level: ServerLevel, destination: ServerLevel, sub: ServerSubLevel) {
        check(IPSableBridge.whyNotMovable(sub, level, destination) == TrainPortalGuard.refusal)
        check(IPSableBridge.moveThroughPortal(sub, level, destination, Vec3(64.0, 100.0, 64.0), null) == null)
        // The seven-argument entry point is used by seamless group handoffs.
        check(IPSableBridge.moveThroughPortal(sub, level, destination, Vec3(64.0, 100.0, 64.0),
            Quaterniond(), null, setOf(sub.uniqueId)) == null)
        check(!sub.isRemoved)
        check(checkNotNull(SubLevelContainer.getContainer(level)).getSubLevel(sub.uniqueId) === sub)
        if (level !== destination) check(checkNotNull(SubLevelContainer.getContainer(destination)).getSubLevel(sub.uniqueId) == null)
    }

    private fun assertScannersSkip(level: ServerLevel, sub: ServerSubLevel) {
        val fallback = Class.forName("qouteall.imm_ptl.core.compat.sable_integration.IPShipPortalTraversalImpl")
        fallback.getDeclaredMethod("considerShip", net.minecraft.server.MinecraftServer::class.java,
            ServerLevel::class.java, ServerSubLevel::class.java, Long::class.javaPrimitiveType).also {
            it.isAccessible = true
        }.invoke(null, level.server, level, sub, level.gameTime)
        val previous = fallback.getDeclaredField("LAST_HULL_CENTER").also { it.isAccessible = true }.get(null) as Map<*, *>
        check(!previous.containsKey(sub.uniqueId)) { "Fallback scanner must skip before changing ship traversal state" }
        val seamless = Class.forName("qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleController")
        val handoffs = mutableListOf<Any>()
        val seen = mutableSetOf<Any>()
        seamless.getDeclaredMethod("considerShip", ServerLevel::class.java, ServerSubLevel::class.java,
            Long::class.javaPrimitiveType, Set::class.java, List::class.java).also {
            it.isAccessible = true
        }.invoke(null, level, sub, level.gameTime, seen, handoffs)
        check(seen.isEmpty() && handoffs.isEmpty())
    }
}
