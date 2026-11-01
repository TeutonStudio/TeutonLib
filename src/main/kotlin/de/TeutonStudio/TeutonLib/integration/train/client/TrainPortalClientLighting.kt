package de.TeutonStudio.TeutonLib.integration.train.client

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormatElement
import dev.ryanhcode.sable.companion.math.Pose3dc
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LightChunk
import net.minecraft.world.level.chunk.LightChunkGetter
import net.minecraft.world.level.lighting.LevelLightEngine
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.Vec3
import org.lwjgl.system.MemoryUtil
import qouteall.imm_ptl.core.ClientWorldLoader
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleClientSessions
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleRender
import java.lang.ref.Cleaner
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.WeakHashMap

/**
 * IP reuses a source mesh when drawing its far half. Its UV2 and Sable's cached sky
 * scale still belong to the source world. Relight only those short-lived draws,
 * retaining geometry, textures, vertex colours, AO and the build's own block light.
 * Sable uses these same vanilla-format meshes when Sodium renders the world.
 */
object TrainPortalClientLighting {
    data class DrawLighting(val level: ResourceKey<Level>, val packedLight: Int,
        val originalPackedLight: Int, val projected: Boolean, val vertices: Int)

    private class Mesh(val ship: WeakReference<ClientSubLevel>, val origin: BlockPos) {
        var original: ByteArray? = null
        var scratch: ByteBuffer? = null
        var allocation: Cleaner.Cleanable? = null
        var revision = 0L
        var applied: DrawKey? = null
        // Two views are drawn repeatedly in nested portal passes. Cache each separately.
        val views = LinkedHashMap<DrawKey, Relit>()
        var sample: Pair<Int, Int>? = null
        fun release() {
            allocation?.clean()
            allocation = null
            scratch = null
            original = null
            sample = null
            views.clear()
            applied = null
        }
    }

    private data class DrawKey(val dimension: ResourceKey<Level>, val projected: Boolean, val tick: Long,
        val pose: List<Double>, val revision: Long)
    private data class Relit(val bytes: ByteArray, val sample: Pair<Int, Int>?)
    private data class Drawing(val level: ClientLevel, val pose: Pose3dc, val projected: Boolean)
    private data class Environment(val light: Int, val normalizedPlotSky: Int)
    private class SkyData(val engine: LevelLightEngine)
    private class NativeVertices(private val bytes: ByteBuffer) : Runnable {
        override fun run() = MemoryUtil.memFree(bytes)
    }

    private val cleaner = Cleaner.create()
    private val meshes = WeakHashMap<VertexBuffer, Mesh>()
    private val skies = WeakHashMap<ClientSubLevel, SkyData>()
    private val draws = WeakHashMap<ClientSubLevel, DrawLighting>()

    fun register(ship: ClientSubLevel, origin: BlockPos, buffer: VertexBuffer) {
        val previous = meshes[buffer]
        if (previous != null && previous.ship.get() === ship && previous.origin == origin) return
        previous?.release()
        meshes[buffer] = Mesh(WeakReference(ship), origin.immutable())
    }

    /** Called before VertexBuffer.upload closes MeshData; no GPU readback is used. */
    fun capture(buffer: VertexBuffer, data: MeshData) {
        val mesh = meshes[buffer] ?: return
        mesh.release()
        if (data.drawState().format() != DefaultVertexFormat.BLOCK) return
        val vertices = data.vertexBuffer().duplicate()
        val size = vertices.remaining()
        mesh.original = ByteArray(size).also { vertices.get(it) }
        mesh.revision++
        mesh.ship.get()?.let { skies.remove(it) }
    }

    fun forget(buffer: VertexBuffer) { meshes.remove(buffer)?.release() }

    fun invalidate(ship: ClientSubLevel) {
        skies.remove(ship)
        meshes.values.filter { it.ship.get() === ship }.forEach { it.views.clear(); it.revision++ }
    }

    private fun drawing(ship: ClientSubLevel): Drawing? {
        val session = IPStraddleRender.projectionSession(ship)
        if (session != null) {
            val target = ClientWorldLoader.getOptionalWorld(session.destDim()) ?: return null
            return Drawing(target, IPStraddleRender.overridePose(ship) ?: return null, true)
        }
        if (IPStraddleClientSessions.renderSessionsOf(ship).isEmpty()) return null
        return Drawing(ship.level, IPStraddleRender.realRenderPose(ship), false)
    }

    /** Neutral shader scale: the relit UV2 already contains the actual world's sky. */
    @JvmStatic fun skyLightScale(ship: ClientSubLevel): Int? = if (drawing(ship) != null) 15 else null

    @JvmStatic fun lastDraw(ship: ClientSubLevel): DrawLighting? = draws[ship]

    /** Also used by block/entity renderers and verification probes at a plot position. */
    @JvmStatic fun samplePackedLight(ship: ClientSubLevel, localPosition: Vec3): Int {
        val context = drawing(ship) ?: Drawing(ship.level, ship.renderPose(), false)
        return sample(ship, context, localPosition, null)
    }

    /** Flywheel's native far-half pass has its own scope, independent of IPStraddleRender. */
    fun samplePackedLightIn(ship: ClientSubLevel, localPosition: Vec3, level: ClientLevel,
        pose: Pose3dc, baked: Int): Int = sample(ship, Drawing(level, pose, level !== ship.level), localPosition, baked)

    private fun sample(ship: ClientSubLevel, context: Drawing, local: Vec3, baked: Int?): Int {
        val environment = environment(ship, context, local)
        val pos = BlockPos.containing(local)
        val source = ship.plot.lightEngine
        val block = baked?.and(0xffff) ?: (source.getLayerListener(LightLayer.BLOCK).getLightValue(pos) shl 4)
        val localSky = if (environment.normalizedPlotSky >= 0) environment.normalizedPlotSky else
            baked?.ushr(16)?.and(0xffff) ?: (source.getLayerListener(LightLayer.SKY).getLightValue(pos) shl 4)
        return TrainPortalLightMath.combine(block or (localSky shl 16), environment.light)
    }

    private fun environment(ship: ClientSubLevel, context: Drawing, local: Vec3): Environment {
        val worldPos = BlockPos.containing(context.pose.transformPosition(local))
        val light = LightTexture.pack(context.level.getBrightness(LightLayer.BLOCK, worldPos),
            context.level.getBrightness(LightLayer.SKY, worldPos))
        // Nether plots have no sky layer. Generate local occlusion with vanilla's
        // skylight solver, independent of the world's sky, rather than using 15.
        val localSky = if (!ship.level.dimensionType().hasSkyLight()) {
            normalizedSky(ship).getLayerListener(LightLayer.SKY).getLightValue(BlockPos.containing(local)) shl 4
        } else -1
        return Environment(light, localSky)
    }

    private fun normalizedSky(ship: ClientSubLevel): LevelLightEngine {
        skies[ship]?.let { return it.engine }
        val weak = WeakReference(ship)
        val chunks = ship.plot.loadedChunks.associate { it.chunk.pos.toLong() to WeakReference(it.chunk) }
        // LevelLightEngine retains its BlockGetter. Do not capture the ClientLevel:
        // that would retain its container and turn this weak cache into a leak.
        val height = ship.level.height
        val minimum = ship.level.minBuildHeight
        val blocks = object : BlockGetter {
            override fun getBlockEntity(pos: BlockPos): BlockEntity? = weak.get()?.level?.getBlockEntity(pos)
            override fun getBlockState(pos: BlockPos): BlockState = weak.get()?.level?.getBlockState(pos)
                ?: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
            override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
            override fun getHeight(): Int = height
            override fun getMinBuildHeight(): Int = minimum
        }
        val getter = object : LightChunkGetter {
            override fun getChunkForLighting(x: Int, z: Int): LightChunk? =
                chunks[net.minecraft.world.level.ChunkPos.asLong(x, z)]?.get()
            override fun getLevel(): BlockGetter = blocks
        }
        val engine = LevelLightEngine(getter, false, true)
        for (chunk in chunks.values.mapNotNull { it.get() }) {
            chunk.initializeLightSources()
            for (index in chunk.sections.indices) {
                if (!chunk.sections[index].hasOnlyAir()) {
                    engine.updateSectionStatus(SectionPos.of(chunk.pos, chunk.getSectionYFromSectionIndex(index)), false)
                }
            }
            engine.setLightEnabled(chunk.pos, true)
        }
        for (chunk in chunks.values.mapNotNull { it.get() }) engine.propagateLightSources(chunk.pos)
        while (engine.hasLightWork()) engine.runLightUpdates()
        skies[ship] = SkyData(engine)
        return engine
    }

    private fun key(mesh: Mesh, context: Drawing): DrawKey {
        val pose = context.pose
        val p = pose.position()
        val q = pose.orientation()
        val r = pose.rotationPoint()
        return DrawKey(context.level.dimension(), context.projected, context.level.gameTime,
            listOf(p.x(), p.y(), p.z(), q.x(), q.y(), q.z(), q.w(), r.x(), r.y(), r.z()), mesh.revision)
    }

    /** The buffer is bound by Sable immediately before this call. */
    fun beforeDraw(buffer: VertexBuffer) {
        val mesh = meshes[buffer] ?: return
        val original = mesh.original ?: return
        val ship = mesh.ship.get() ?: return
        val context = drawing(ship)
        if (context == null) {
            if (mesh.applied != null) upload(buffer, mesh, original)
            mesh.applied = null
            mesh.views.clear()
            return
        }
        val key = key(mesh, context)
        if (mesh.applied != key) {
            val relit = mesh.views[key] ?: relight(mesh, ship, context, original).also {
                // Bound memory even if many nested views or interpolated poses occur.
                if (mesh.views.size >= 4) mesh.views.remove(mesh.views.keys.first())
                mesh.views[key] = it
            }
            upload(buffer, mesh, relit.bytes)
            mesh.sample = relit.sample
            mesh.applied = key
        }
        mesh.sample?.let { (packed, before) -> draws[ship] = DrawLighting(context.level.dimension(),
            packed, before, context.projected, original.size / DefaultVertexFormat.BLOCK.vertexSize) }
    }

    private fun relight(mesh: Mesh, ship: ClientSubLevel, context: Drawing, original: ByteArray): Relit {
        val result = original.copyOf()
        val bytes = ByteBuffer.wrap(result).order(ByteOrder.nativeOrder())
        val format = DefaultVertexFormat.BLOCK
        val stride = format.vertexSize
        val position = format.getOffset(VertexFormatElement.POSITION)
        val normal = format.getOffset(VertexFormatElement.NORMAL)
        val light = format.getOffset(VertexFormatElement.UV2)
        var brightest = -1
        var sample: Pair<Int, Int>? = null
        // Section meshes use independent four-vertex quads. Sample just outside
        // the face, avoiding an opaque block at the exact shared vertex boundary.
        for (quad in 0 until result.size / (stride * 4)) {
            var x = 0.0; var y = 0.0; var z = 0.0
            val first = quad * stride * 4
            for (vertex in 0..3) {
                val offset = first + vertex * stride + position
                x += bytes.getFloat(offset); y += bytes.getFloat(offset + 4); z += bytes.getFloat(offset + 8)
            }
            val local = Vec3(mesh.origin.x + x * .25 + bytes.get(first + normal).toDouble() / 127.0 * .05,
                mesh.origin.y + y * .25 + bytes.get(first + normal + 1).toDouble() / 127.0 * .05,
                mesh.origin.z + z * .25 + bytes.get(first + normal + 2).toDouble() / 127.0 * .05)
            // The four vertices share this face sample. Keep their individual
            // baked interpolation fractions while querying world/plot light once.
            val environment = environment(ship, context, local)
            for (vertex in 0..3) {
                val offset = first + vertex * stride + light
                val before = bytes.getInt(offset)
                val sky = if (environment.normalizedPlotSky >= 0) environment.normalizedPlotSky else before ushr 16
                val packed = TrainPortalLightMath.combine((before and 0xffff) or (sky shl 16), environment.light)
                bytes.putInt(offset, packed)
                val brightness = maxOf(packed and 0xffff, packed ushr 16)
                if (brightness > brightest) { brightest = brightness; sample = packed to before }
            }
        }
        return Relit(result, sample)
    }

    private fun upload(buffer: VertexBuffer, mesh: Mesh, bytes: ByteArray) {
        val scratch = mesh.scratch ?: MemoryUtil.memAlloc(bytes.size).order(ByteOrder.nativeOrder()).also {
            mesh.scratch = it
            // Allocate staging only once the ship actually straddles a portal.
            // close frees immediately; phantom cleanup covers abandoned buffers.
            mesh.allocation = cleaner.register(buffer, NativeVertices(it))
        }
        scratch.clear().put(bytes).flip()
        (buffer as TrainPortalVertexBufferAccess).`teutonlib$uploadLighting`(scratch)
    }
}
