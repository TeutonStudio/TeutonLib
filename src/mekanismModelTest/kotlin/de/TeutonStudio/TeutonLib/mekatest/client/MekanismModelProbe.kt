package de.TeutonStudio.TeutonLib.mekatest.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import mekanism.common.tile.transmitter.TileEntityTransmitter
import mekanism.common.lib.transmitter.ConnectionType
import mekanism.common.network.to_client.PacketUpdateTile
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.chunk.RenderRegionCache
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.client.model.data.ModelData
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Development-only observations of the actual vanilla/reacharound compilation path. */
object MekanismModelProbe {
    private data class Key(val dimension: String, val position: Long)
    private data class Data(val present: Boolean, val modes: Map<String, String>, val colored: Boolean?) {
        fun json() = JsonObject().apply {
            addProperty("present", present)
            add("modes", JsonObject().also { o -> modes.forEach { (side, mode) -> o.addProperty(side, mode) } })
            colored?.let { addProperty("colored", it) }
        }
    }
    private data class Compile(val input: Data, val output: Data, val model: String, val thread: String)
    private data class Quad(val data: Data, val count: Int, val fingerprint: String)
    private class Record {
        var compiles = 0L
        var renders = 0L
        var quads = 0L
        val history = ArrayDeque<Compile>()
        val latestQuads = linkedMapOf<String, Quad>()
        var unculledCutout: Quad? = null
    }
    private data class Context(val key: Key)
    private val records = ConcurrentHashMap<Key, Record>()
    private val context = ThreadLocal<Context?>()
    private val packetSequence = AtomicLong()
    private val packetTrace = ArrayDeque<JsonObject>()
    private val connectionPacketCount = AtomicLong()
    private val connectionPacketTrace = ArrayDeque<JsonObject>()
    private val activePacket = ThreadLocal<JsonObject?>()
    private class Updates {
        var count = 0L
        val history = ArrayDeque<JsonObject>()
    }
    private val tileUpdates = ConcurrentHashMap<Key, Updates>()

    @JvmStatic
    fun reset() {
        records.clear()
        tileUpdates.clear()
        synchronized(packetTrace) { packetTrace.clear() }
        synchronized(connectionPacketTrace) { connectionPacketTrace.clear() }
        packetSequence.set(0)
        connectionPacketCount.set(0)
    }

    private fun position(pos: BlockPos) = JsonArray().also { it.add(pos.x); it.add(pos.y); it.add(pos.z) }

    private fun tagModes(tag: CompoundTag) = JsonObject().also { result ->
        val raw = tag.getIntArray("connection")
        Direction.entries.forEach { side ->
            raw.getOrNull(side.ordinal)?.let { result.addProperty(side.name, ConnectionType.entries.getOrNull(it)?.name ?: "invalid:$it") }
        }
    }

    /** Wraps the complete native handler, including CMC's cancelling HEAD injection. */
    @JvmStatic
    fun beginPacket(packet: PacketUpdateTile, payloadContext: IPayloadContext): Any? {
        val previous = activePacket.get()
        val level = payloadContext.player().level() as? ClientLevel
        val hasConnectionPayload = packet.updateTag().getIntArray("connection").isNotEmpty()
        val receipt = JsonObject().apply {
            addProperty("sequence", packetSequence.incrementAndGet())
            addProperty("thread", Thread.currentThread().name)
            addProperty("clientThread", Minecraft.getInstance().isSameThread)
            addProperty("dimension", level?.dimension()?.location()?.toString())
            addProperty("clientTick", level?.gameTime)
            add("position", position(packet.pos()))
            add("payloadModes", tagModes(packet.updateTag()))
            addProperty("hasConnectionPayload", hasConnectionPayload)
            if (packet.updateTag().contains("connections")) addProperty("connections", packet.updateTag().getByte("connections"))
            if (packet.updateTag().contains("acceptors")) addProperty("acceptors", packet.updateTag().getByte("acceptors"))
            add("appliedTiles", JsonArray())
        }
        if (level != null && Minecraft.getInstance().isSameThread) {
            receipt.addProperty("directTileClass", level.getBlockEntity(packet.pos())?.javaClass?.name)
            receipt.add("mountedCandidates", JsonArray().also { candidates ->
                SubLevelContainer.getContainer(level)?.allSubLevels?.forEach { sub ->
                    for (chunk in sub.plot.loadedChunks) for (tile in chunk.chunk.blockEntities.values) {
                        if (tile !is TileEntityTransmitter) continue
                        val physical = sub.logicalPose().transformPosition(Vec3.atCenterOf(tile.blockPos))
                        val physicalCell = BlockPos.containing(physical)
                        candidates.add(JsonObject().apply {
                            addProperty("ship", sub.uniqueId.toString())
                            add("plotPosition", position(tile.blockPos))
                            add("physicalCell", position(physicalCell))
                            addProperty("physicalCellMatchesPacket", physicalCell == packet.pos())
                            addProperty("packetIsPlotPosition", tile.blockPos == packet.pos())
                            addProperty("distanceSquared", physical.distanceToSqr(Vec3.atCenterOf(packet.pos())))
                            add("modesBefore", data(tile.modelData).json())
                        })
                    }
                }
            })
        }
        synchronized(packetTrace) {
            packetTrace.addLast(receipt)
            while (packetTrace.size > 64) packetTrace.removeFirst()
        }
        if (hasConnectionPayload) {
            connectionPacketCount.incrementAndGet()
            synchronized(connectionPacketTrace) {
                connectionPacketTrace.addLast(receipt)
                while (connectionPacketTrace.size > 64) connectionPacketTrace.removeFirst()
            }
        }
        activePacket.set(receipt)
        return previous
    }

    @JvmStatic
    fun endPacket(previous: Any?) {
        activePacket.get()?.addProperty("completed", true)
        if (previous == null) activePacket.remove() else activePacket.set(previous as JsonObject)
    }

    @JvmStatic
    fun recordTileUpdate(tile: TileEntityTransmitter, tag: CompoundTag) {
        val level = tile.level as? ClientLevel ?: return
        val sample = JsonObject().apply {
            addProperty("clientTick", level.gameTime)
            add("position", position(tile.blockPos))
            add("payloadModes", tagModes(tag))
            add("modesAfter", data(tile.modelData).json())
            activePacket.get()?.get("sequence")?.let { add("packetSequence", it) }
        }
        val record = tileUpdates.computeIfAbsent(Key(level.dimension().location().toString(), tile.blockPos.asLong())) { Updates() }
        synchronized(record) {
            record.count++
            record.history.addLast(sample)
            while (record.history.size > 32) record.history.removeFirst()
        }
        activePacket.get()?.getAsJsonArray("appliedTiles")?.add(sample.deepCopy())
    }

    private fun data(modelData: ModelData): Data {
        val transmitter = modelData.get(TileEntityTransmitter.TRANSMITTER_PROPERTY)
            ?: return Data(false, emptyMap(), null)
        return Data(true, Direction.entries.associate { it.name to transmitter.getConnectionType(it).name }, transmitter.hasColor)
    }

    private fun key(view: BlockAndTintGetter, pos: BlockPos): Key? {
        val level = view.getBlockEntity(pos)?.level as? ClientLevel ?: return null
        return Key(level.dimension().location().toString(), pos.asLong())
    }

    @JvmStatic
    fun recordCompile(view: BlockAndTintGetter, pos: BlockPos, state: BlockState, model: BakedModel,
        input: ModelData, output: ModelData) {
        if (view.getBlockEntity(pos) !is TileEntityTransmitter) return
        val record = records.computeIfAbsent(key(view, pos) ?: return) { Record() }
        synchronized(record) {
            record.compiles++
            record.latestQuads.clear()
            record.unculledCutout = null
            record.history.addLast(Compile(data(input), data(output), model.javaClass.name, Thread.currentThread().name))
            while (record.history.size > 16) record.history.removeFirst()
        }
    }

    @JvmStatic
    fun beginRender(view: BlockAndTintGetter, pos: BlockPos): Any? {
        val previous = context.get()
        val key = if (view.getBlockEntity(pos) is TileEntityTransmitter) key(view, pos) else null
        context.set(key?.let { Context(it) })
        if (key != null) records.computeIfAbsent(key) { Record() }.also { synchronized(it) { it.renders++ } }
        return previous
    }

    @JvmStatic
    fun endRender(previous: Any?) {
        if (previous == null) context.remove() else context.set(previous as Context)
    }

    @JvmStatic
    fun recordQuads(side: Direction?, layer: RenderType?, modelData: ModelData, quads: List<BakedQuad>) {
        val key = context.get()?.key ?: return
        val sample = Quad(data(modelData), quads.size, fingerprint(quads))
        val record = records.computeIfAbsent(key) { Record() }
        synchronized(record) {
            record.quads++
            record.latestQuads["${layer ?: "null"}/${side?.name ?: "unculled"}"] = sample
            if (side == null && layer === RenderType.cutout()) record.unculledCutout = sample
        }
    }

    private fun fingerprint(quads: List<BakedQuad>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        quads.forEach { quad ->
            digest.update(quad.direction.name.toByteArray(Charsets.UTF_8))
            digest.update(quad.sprite.contents().name().toString().toByteArray(Charsets.UTF_8))
            val buffer = ByteBuffer.allocate(quad.vertices.size * Int.SIZE_BYTES)
            quad.vertices.forEach(buffer::putInt)
            digest.update(buffer.array())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Main-client-thread probe; worker observers above only copy immutable connection values. */
    @JvmStatic
    fun snapshot(level: ClientLevel, pos: BlockPos): JsonObject {
        check(Minecraft.getInstance().isSameThread) { "Mekanism model snapshots require the client thread" }
        val result = JsonObject()
        val key = Key(level.dimension().location().toString(), pos.asLong())
        val tile = level.getBlockEntity(pos)
        result.addProperty("position", pos.toShortString())
        result.addProperty("dimension", key.dimension)
        result.addProperty("tileClass", tile?.javaClass?.name)
        val tileModelData = tile?.modelData ?: ModelData.EMPTY
        val tileData = data(tileModelData)
        val managerData = data(level.modelDataManager?.getAt(pos) ?: ModelData.EMPTY)
        result.add("tileData", tileData.json())
        result.add("managerData", managerData.json())
        val region = RenderRegionCache().createRegion(level, SectionPos.of(pos))
        result.addProperty("regionClass", region?.javaClass?.name)
        val regionData = region?.getModelData(pos) ?: ModelData.EMPTY
        result.add("regionData", data(regionData).json())
        val state = level.getBlockState(pos)
        val model = Minecraft.getInstance().blockRenderer.getBlockModel(state)
        result.addProperty("modelClass", model.javaClass.name)
        val resolved = if (region == null) ModelData.EMPTY else model.getModelData(region, pos, state, regionData)
        result.add("resolvedData", data(resolved).json())
        result.add("tileModes", tileData.json().get("modes"))
        result.add("managerModes", managerData.json().get("modes"))
        result.add("regionModes", data(regionData).json().get("modes"))
        result.add("resolvedModes", data(resolved).json().get("modes"))
        val expected = if (region == null) tileModelData else model.getModelData(region, pos, state, tileModelData)
        val expectedQuads = linkedMapOf<String, Quad>()
        var directUnculledCutout: Quad? = null
        val random = RandomSource.create(state.getSeed(pos))
        result.add("directQuads", JsonObject().also { layers ->
            for (layer in model.getRenderTypes(state, random, expected).asList()) {
                val quads = buildList {
                    for (side in Direction.entries + listOf(null)) {
                        random.setSeed(state.getSeed(pos))
                        val face = model.getQuads(state, side, random, expected, layer)
                        val sample = Quad(data(expected), face.size, fingerprint(face))
                        expectedQuads["$layer/${side?.name ?: "unculled"}"] = sample
                        if (side == null && layer === RenderType.cutout()) directUnculledCutout = sample
                        addAll(face)
                    }
                }
                layers.add(layer.toString(), JsonObject().apply {
                    addProperty("count", quads.size)
                    addProperty("fingerprint", fingerprint(quads))
                })
            }
        })
        val dataMatches = tile is TileEntityTransmitter && tileData == managerData && tileData == data(regionData) && tileData == data(resolved)
        var actualObserved = false
        var actualDataMatches = false
        var actualQuadsMatch = false
        var actualUnculledCutout: Quad? = null
        result.add("actualCompile", JsonObject().also { actual ->
            val record = records[key]
            if (record == null) {
                actual.addProperty("compiles", 0)
                actual.addProperty("renders", 0)
                actual.addProperty("quadCalls", 0)
            } else synchronized(record) {
                actualObserved = record.compiles > 0
                actualDataMatches = record.history.lastOrNull()?.let { it.input == tileData && it.output == tileData } == true
                actualQuadsMatch = record.latestQuads.isNotEmpty() && record.latestQuads.all { (face, quad) -> quad == expectedQuads[face] }
                actualUnculledCutout = record.unculledCutout
                actual.addProperty("compiles", record.compiles)
                actual.addProperty("renders", record.renders)
                actual.addProperty("quadCalls", record.quads)
                actual.add("history", JsonArray().also { history -> record.history.forEach { sample ->
                    history.add(JsonObject().apply {
                        add("input", sample.input.json())
                        add("output", sample.output.json())
                        addProperty("model", sample.model)
                        addProperty("thread", sample.thread)
                    })
                } })
                actual.add("latestQuads", JsonObject().also { samples -> record.latestQuads.forEach { (face, sample) ->
                    samples.add(face, JsonObject().apply {
                        add("data", sample.data.json())
                        addProperty("count", sample.count)
                        addProperty("fingerprint", sample.fingerprint)
                    })
                } })
            }
        })
        result.addProperty("dataMatches", dataMatches)
        result.addProperty("actualCompileObserved", actualObserved)
        result.addProperty("actualCompileDataMatches", actualDataMatches)
        result.addProperty("actualCompileQuadsMatch", actualQuadsMatch)
        // Independent installed-OBJ count: two PULL/PUSH arms contribute 20 each,
        // and the four NONE ends contribute one quad each.
        val expectedSpecialEndQuadCount = 44
        val directGeometryMatches = directUnculledCutout?.count == expectedSpecialEndQuadCount
        val actualGeometryMatches = actualUnculledCutout?.count == expectedSpecialEndQuadCount
        result.addProperty("expectedSpecialEndQuadCount", expectedSpecialEndQuadCount)
        result.addProperty("directUnculledCutoutCount", directUnculledCutout?.count ?: 0)
        result.addProperty("directUnculledCutoutFingerprint", directUnculledCutout?.fingerprint)
        result.addProperty("actualUnculledCutoutCount", actualUnculledCutout?.count ?: 0)
        result.addProperty("actualUnculledCutoutFingerprint", actualUnculledCutout?.fingerprint)
        result.addProperty("directSpecialEndGeometryMatches", directGeometryMatches)
        result.addProperty("actualSpecialEndGeometryMatches", actualGeometryMatches)
        result.addProperty("actualNonemptyUnculledObserved", (actualUnculledCutout?.count ?: 0) > 0)
        result.addProperty("passed", dataMatches && directGeometryMatches &&
            (!actualObserved || actualDataMatches && actualQuadsMatch && actualGeometryMatches))
        result.add("packets", JsonArray().also { samples ->
            synchronized(packetTrace) { packetTrace.forEach { samples.add(it.deepCopy()) } }
        })
        result.addProperty("packetCount", packetSequence.get())
        result.addProperty("connectionPacketCount", connectionPacketCount.get())
        result.add("connectionPackets", JsonArray().also { samples ->
            synchronized(connectionPacketTrace) { connectionPacketTrace.forEach { samples.add(it.deepCopy()) } }
        })
        result.add("tileUpdates", JsonObject().also { updates ->
            val record = tileUpdates[key]
            if (record == null) updates.addProperty("count", 0)
            else synchronized(record) {
                updates.addProperty("count", record.count)
                updates.add("history", JsonArray().also { samples -> record.history.forEach { samples.add(it.deepCopy()) } })
            }
        })
        return result
    }
}
