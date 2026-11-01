package de.TeutonStudio.TeutonLib.traintest

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.simibubi.create.AllBlocks
import com.simibubi.create.api.contraption.train.PortalTrackProvider
import com.simibubi.create.content.trains.track.TrackBlock
import com.simibubi.create.content.trains.track.TrackBlockEntity
import com.simibubi.create.content.trains.track.TrackShape
import net.createmod.catnip.math.BlockFace
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayer
import qouteall.imm_ptl.core.IPPerServerInfo
import qouteall.imm_ptl.core.compat.create_integration.IPCreatePortalTracks
import qouteall.imm_ptl.core.portal.Portal
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenTrigger.UseItemTrigger
import qouteall.imm_ptl.core.portal.custom_portal_gen.form.HeterogeneousForm
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal
import java.security.MessageDigest
import kotlin.math.abs

/** Uses Aether's installed compatibility pack and real water-bucket item hook. */
object NativeAetherPortalVerification {
    private const val SOURCE_BLOCK_X = 80
    private const val TARGET_BLOCK_X = 160
    private const val NATIVE_TAG = "aether:water_bucket_create_aether_portal_to"
    private const val TRACK_Y = 100
    private val generationId = ResourceLocation.parse(NATIVE_TAG)
    private val definitionResource = ResourceLocation.fromNamespaceAndPath("aether",
        "custom_portal_generation/water_bucket_create_aether_portal_to.json")
    private val sourceCenter = Vec3(SOURCE_BLOCK_X + 0.5, 103.0, 0.0)
    private val targetCenter = Vec3(TARGET_BLOCK_X + 0.5, 103.0, 0.0)

    /** Retained independently of individual visual/rider fixtures and their cleanup. */
    data class Pairing(val source: ServerLevel, val destination: ServerLevel,
        val forward: Portal, val reverse: Portal, val portals: List<Portal>,
        val sourceTrack: BlockPos, val destinationTrack: BlockPos) {
        val sourceX: Double get() = forward.originPos.x
        val targetX: Double get() = reverse.originPos.x
    }

    private data class Preparation(val source: ServerLevel, val destination: ServerLevel,
        val started: Int, val sourcePackId: String, val definitionSha256: String,
        var pairing: Pairing? = null, var failure: String? = null)
    private var preparation: Preparation? = null

    fun isRequested(): Boolean = TrainPortalTestDestination.key.location().toString() == "aether:the_aether"

    /** Returns immediately; status() waits for IP's asynchronous native frame search. */
    fun prepare(source: ServerLevel, player: ServerPlayer): String {
        check(isRequested()) { "Native Aether preparation requires teutonlib.trainPortalDestination=aether:the_aether" }
        check(source.dimension() == Level.OVERWORLD) { "Native Aether source must be the Overworld" }
        check(player.server === source.server && (player is FakePlayer || !player.hasDisconnected())) {
            "A connected ServerPlayer or NeoForge server-only FakePlayer is required"
        }
        preparation?.takeIf { it.source === source }?.let { return status(source.server) }
        val destination = TrainPortalTestDestination.level(source)
        // NeoForge can merge built-in packs into a mod pack. Verify the effective
        // resource and decoded registry entry, rather than a repository display ID.
        val resource = source.server.resourceManager.getResource(definitionResource).orElseThrow {
            IllegalStateException("Aether's native water-bucket datapack resource is not active: $definitionResource")
        }
        val definitionBytes = resource.open().use { it.readBytes() }
        val definitionHash = MessageDigest.getInstance("SHA-256").digest(definitionBytes)
            .joinToString("") { "%02x".format(it) }
        val generation = source.registryAccess().registryOrThrow(CustomPortalGeneration.LEGACY_REGISTRY_KEY).get(generationId)
            ?: error("Native Aether water-bucket generation is absent from the loaded registry")
        val form = generation.form as? HeterogeneousForm
        val trigger = generation.trigger as? UseItemTrigger
        check(form != null && form.generateFrameIfNotFound && form.areaBlock.name == "minecraft:air" &&
            form.frameBlock.name == "#aether:aether_portal_blocks" && form.frameBlock.test(Blocks.GLOWSTONE.defaultBlockState()) &&
            !form.frameBlock.test(Blocks.STONE.defaultBlockState()) && trigger?.item === Items.WATER_BUCKET &&
            generation.identifier == generationId && generation.spaceRatioFrom == 1 && generation.spaceRatioTo == 1 &&
            generation.fromDimensions == listOf(Level.OVERWORLD) && generation.toDimension == destination.dimension()) {
            "Unexpected native Aether generation definition: $generation"
        }
        check(IPPerServerInfo.of(source.server).customPortalGenManager != null) { "IP datapack portal generation is disabled" }
        forceFrameChunks(source, SOURCE_BLOCK_X)
        forceFrameChunks(destination, TARGET_BLOCK_X)
        val active = Preparation(source, destination, source.server.tickCount, resource.sourcePackId(), definitionHash)
        preparation = active
        val existing = candidates(active)
        if (existing.isNotEmpty()) {
            check(existing.size == 4) { "Incomplete native Aether cluster already exists: ${existing.size}" }
            return status(source.server)
        }
        buildFrame(source, SOURCE_BLOCK_X)
        buildFrame(destination, TARGET_BLOCK_X)
        val oldHand = player.getItemInHand(InteractionHand.MAIN_HAND)
        val oldMode = player.gameMode.gameModeForPlayer
        val water = ItemStack(Items.WATER_BUCKET)
        try {
            player.setGameMode(GameType.CREATIVE)
            player.setItemInHand(InteractionHand.MAIN_HAND, water)
            // Clicking the inside of the bottom Glowstone leaves the interior empty.
            // BucketItem.useOn returns PASS, but IP's real ItemStack RETURN hook queues
            // the generation registered by Aether's own installed datapack.
            val bottom = BlockPos(SOURCE_BLOCK_X, 96, 0)
            val hit = BlockHitResult(Vec3(SOURCE_BLOCK_X + 0.5, 97.0, 0.5), Direction.UP, bottom, false)
            water.useOn(UseOnContext(source, player, InteractionHand.MAIN_HAND, water, hit))
        } catch (failure: Throwable) {
            active.failure = failure.toString()
            throw failure
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand)
            player.setGameMode(oldMode)
            player.inventoryMenu.broadcastChanges()
        }
        return "PREPARING native-aether water-bucket generation=$NATIVE_TAG"
    }

    fun status(server: MinecraftServer): String {
        val active = preparation?.takeIf { it.source.server === server } ?: return "IDLE native-aether"
        active.failure?.let { return "FAIL native-aether $it" }
        try {
            // Other contract fixtures can leave the remote dimension unloaded.
            // Reacquire these existing chunks and their native forced tickets;
            // entity storage becomes accessible on a subsequent server tick.
            forceFrameChunks(active.source, SOURCE_BLOCK_X)
            forceFrameChunks(active.destination, TARGET_BLOCK_X)
            val all = candidates(active)
            check(all.size <= 4) { "Expected one native Aether portal cluster, found ${all.size}" }
            val accessible = all.size == 4 && all.all { portal ->
                val level = portal.level() as ServerLevel
                level.getEntity(portal.uuid) === portal && level.isPositionEntityTicking(portal.blockPosition())
            }
            if (!accessible) {
                check(server.tickCount - active.started < 2400) { "Native Aether generation timed out: portals=${all.size}" }
                val known = active.pairing?.let { pair -> JsonArray().also { array ->
                    for (portal in pair.portals) {
                        val level = portal.level() as ServerLevel
                        val position = portal.blockPosition()
                        val resolved = level.getEntity(portal.uuid)
                        array.add(JsonObject().also {
                            it.addProperty("uuid", portal.uuid.toString())
                            it.addProperty("dimension", level.dimension().location().toString())
                            it.addProperty("inSpatialQuery", all.any { candidate -> candidate.uuid == portal.uuid })
                            it.addProperty("knownRemoved", portal.isRemoved)
                            it.addProperty("removalReason", portal.removalReason?.name)
                            it.addProperty("resolved", resolved != null)
                            it.addProperty("sameInstance", resolved === portal)
                            it.addProperty("resolvedRemoved", resolved?.isRemoved)
                            it.addProperty("resolvedType", resolved?.javaClass?.name)
                            it.addProperty("chunkLoaded", level.chunkSource.hasChunk(position.x shr 4, position.z shr 4))
                            it.addProperty("chunkForced", level.forcedChunks.contains(ChunkPos.asLong(position.x shr 4, position.z shr 4)))
                            it.addProperty("entityTicking", level.isPositionEntityTicking(position))
                            it.addProperty("position", portal.position().toString())
                            it.addProperty("bounds", portal.boundingBox.toString())
                        })
                    }
                } }
                return "PREPARING native-aether portals=${all.size}/4 ticks=${server.tickCount - active.started}" +
                    if (known != null) " known=$known" else ""
            }
            check(all.size == 4) { "Expected one native Aether portal cluster, found ${all.size}" }
            verifyFrame(active.source, SOURCE_BLOCK_X)
            verifyFrame(active.destination, TARGET_BLOCK_X)
            all.forEach { portal ->
                val from = if (portal.level() === active.source) sourceCenter else targetCenter
                val to = if (portal.level() === active.source) targetCenter else sourceCenter
                val target = if (portal.level() === active.source) active.destination else active.source
                check(portal.portalTag == NATIVE_TAG && portal.javaClass == GeneralBreakablePortal::class.java && portal.isIntactNow &&
                    portal.originPos.distanceToSqr(from) < 1e-8 && portal.destPos.distanceToSqr(to) < 1e-8 &&
                    portal.destDim == target.dimension() && abs(portal.width - 12.0) < 1e-6 &&
                    abs(portal.height - 12.0) < 1e-6 && abs(portal.scale - 1.0) < 1e-6) {
                    "Native Aether portal class/tag/frame/transform mismatch: $portal tag=${portal.portalTag}"
                }
            }
            val forward = all.single { it.level() === active.source && it.normal.x < -0.99 }
            val reverse = all.single { it.level() === active.destination && it.normal.x > 0.99 }
            check(forward.reversePortalId == reverse.uuid && reverse.reversePortalId == forward.uuid &&
                Portal.isReversePortal(forward, reverse)) { "Native Aether portal cluster has no reciprocal west/east pair" }
            val previousPair = active.pairing
            if (previousPair == null) {
                val near = BlockPos(SOURCE_BLOCK_X - 1, TRACK_Y, 0)
                val far = BlockPos(TARGET_BLOCK_X + 1, TRACK_Y, 0)
                installAndVerifyTracks(active, near, far)
                active.pairing = Pairing(active.source, active.destination, forward, reverse, all, near, far)
            } else {
                check(all.map { it.uuid }.toSet() == previousPair.portals.map { it.uuid }.toSet()) {
                    "Reload replaced the original native Aether portal UUIDs"
                }
                // UNLOADED_TO_CHUNK invalidates the old objects, while the saved
                // native entities retain their UUIDs, frame and track bindings.
                active.pairing = previousPair.copy(forward = forward, reverse = reverse, portals = all)
            }
            val pair = checkNotNull(active.pairing)
            verifyBinding(pair.source, pair.sourceTrack, pair.destination, pair.destinationTrack)
            verifyBinding(pair.destination, pair.destinationTrack, pair.source, pair.sourceTrack)
            return "READY ${report(pair, active)}"
        } catch (failure: Throwable) {
            active.failure = failure.toString()
            return "FAIL native-aether ${active.failure}"
        }
    }

    fun pair(source: ServerLevel): Pairing {
        val report = status(source.server)
        check(report.startsWith("READY ")) { report }
        return checkNotNull(preparation?.takeIf { it.source === source }?.pairing) { "Native Aether source changed" }
    }

    /** Explicit end-of-suite cleanup only; ordinary fixture cleanup must retain pair().portals. */
    fun cleanup(server: MinecraftServer): String {
        val active = preparation?.takeIf { it.source.server === server } ?: return "IDLE native-aether"
        candidates(active).forEach { it.discard() }
        for ((level, x) in listOf(active.source to SOURCE_BLOCK_X, active.destination to TARGET_BLOCK_X)) {
            for (y in 96..109) for (z in -7..6) level.setBlockAndUpdate(BlockPos(x, y, z), Blocks.AIR.defaultBlockState())
        }
        active.pairing?.let { pair ->
            pair.source.setBlockAndUpdate(pair.sourceTrack, Blocks.AIR.defaultBlockState())
            pair.destination.setBlockAndUpdate(pair.destinationTrack, Blocks.AIR.defaultBlockState())
        }
        preparation = null
        return "CLEANED native-aether"
    }

    private fun candidates(active: Preparation): List<GeneralBreakablePortal> =
        listOf(active.source to sourceCenter, active.destination to targetCenter).flatMap { (level, center) ->
            level.getEntitiesOfClass(GeneralBreakablePortal::class.java, AABB(center, center).inflate(8.0)) {
                !it.isRemoved && it.portalTag == NATIVE_TAG && it.originPos.distanceToSqr(center) < 1e-8
            }
        }

    private fun forceFrameChunks(level: ServerLevel, x: Int) {
        for (cx in ((x - 2) shr 4)..((x + 2) shr 4)) for (cz in -1..0) {
            val position = ChunkPos(cx, cz)
            if (!level.forcedChunks.contains(position.toLong())) level.setChunkForced(cx, cz, true)
            else level.chunkSource.updateChunkForced(position, true)
            level.getChunk(cx, cz)
        }
    }

    private fun buildFrame(level: ServerLevel, x: Int) {
        for (y in 96..109) for (z in -7..6) {
            val border = y == 96 || y == 109 || z == -7 || z == 6
            level.setBlockAndUpdate(BlockPos(x, y, z), if (border) Blocks.GLOWSTONE.defaultBlockState() else Blocks.AIR.defaultBlockState())
        }
    }

    private fun verifyFrame(level: ServerLevel, x: Int) {
        for (y in 96..109) for (z in -7..6) {
            val position = BlockPos(x, y, z)
            val border = y == 96 || y == 109 || z == -7 || z == 6
            check(level.getBlockState(position).`is`(if (border) Blocks.GLOWSTONE else PortalPlaceholderBlock.instance)) {
                "Native Aether ${if (border) "Glowstone frame" else "placeholder"} missing at $position in ${level.dimension().location()}"
            }
        }
    }

    private fun installAndVerifyTracks(active: Preparation, near: BlockPos, far: BlockPos) {
        val straight = AllBlocks.TRACK.defaultState.setValue(TrackBlock.SHAPE, TrackShape.XO)
        active.source.setBlockAndUpdate(near.below(), Blocks.STONE.defaultBlockState())
        active.destination.setBlockAndUpdate(far.below(), Blocks.STONE.defaultBlockState())
        active.source.setBlockAndUpdate(near, straight)
        active.destination.setBlockAndUpdate(far, straight)
        val nearExit = checkNotNull(PortalTrackProvider.getOtherSide(active.source, BlockFace(near, Direction.EAST))) {
            "Create's registered portal-track provider rejected the native Aether source portal"
        }
        val farExit = checkNotNull(PortalTrackProvider.getOtherSide(active.destination, BlockFace(far, Direction.WEST))) {
            "Create's registered portal-track provider rejected the native Aether return portal"
        }
        check(nearExit.level() === active.destination && nearExit.face().pos == far && nearExit.face().face == Direction.WEST)
        check(farExit.level() === active.source && farExit.face().pos == near && farExit.face().face == Direction.EAST)
        check(IPCreatePortalTracks.connect(active.source, near, active.source.getBlockState(near))) {
            "Native IP/Create track link did not recognize Aether's placeholder"
        }
        verifyBinding(active.source, near, active.destination, far)
        verifyBinding(active.destination, far, active.source, near)
    }

    private fun verifyBinding(level: ServerLevel, position: BlockPos, destination: ServerLevel, other: BlockPos) {
        val track = level.getBlockEntity(position) as? TrackBlockEntity ?: error("Native portal track BE missing at $position")
        val bound = checkNotNull(track.boundLocation) { "Native portal track at $position has no boundLocation" }
        check(bound.first == destination.dimension() && bound.second == other &&
            track.persistentData.getBoolean("ip_aeronautics:own_portal_track")) {
            "Native portal track binding at $position is not reciprocal: $bound"
        }
    }

    private fun report(pair: Pairing, active: Preparation): JsonObject = JsonObject().apply {
        addProperty("sourceX", pair.sourceX)
        addProperty("targetX", pair.targetX)
        addProperty("source", pair.source.dimension().location().toString())
        addProperty("destination", pair.destination.dimension().location().toString())
        addProperty("generation", NATIVE_TAG)
        addProperty("definitionResource", definitionResource.toString())
        addProperty("sourcePackId", active.sourcePackId)
        addProperty("definitionSha256", active.definitionSha256)
        addProperty("portalCount", pair.portals.size)
        addProperty("frame", "glowstone")
        addProperty("placeholderCountPerSide", 144)
        addProperty("tracks", "bidirectional-bound")
        add("sourceTrack", point(Vec3.atLowerCornerOf(pair.sourceTrack)))
        add("destinationTrack", point(Vec3.atLowerCornerOf(pair.destinationTrack)))
        add("portals", JsonArray().apply {
            pair.portals.forEach { portal -> add(JsonObject().apply {
                addProperty("uuid", portal.uuid.toString())
                addProperty("class", portal.javaClass.name)
                addProperty("world", portal.level().dimension().location().toString())
                addProperty("destinationWorld", portal.destDim.location().toString())
                addProperty("tag", portal.portalTag)
                add("origin", point(portal.originPos))
                add("normal", point(portal.normal))
                add("destination", point(portal.destPos))
            }) }
        })
    }

    private fun point(value: Vec3) = JsonArray().apply { add(value.x); add(value.y); add(value.z) }
}
