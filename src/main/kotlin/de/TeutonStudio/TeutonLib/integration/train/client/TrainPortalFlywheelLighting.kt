package de.TeutonStudio.TeutonLib.integration.train.client

import dev.engine_room.flywheel.api.backend.RenderContext
import dev.engine_room.flywheel.backend.engine.EngineImpl
import dev.engine_room.flywheel.backend.engine.AbstractInstancer
import dev.engine_room.flywheel.backend.engine.BaseInstancer
import dev.engine_room.flywheel.backend.engine.LightStorage
import dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage
import dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer
import dev.engine_room.flywheel.lib.instance.ColoredLitInstance
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.Pose3dc
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelLightStorage
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2IntMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.SectionPos
import net.minecraft.world.phys.Vec3
import org.lwjgl.system.MemoryUtil
import qouteall.imm_ptl.core.ClientWorldLoader
import qouteall.imm_ptl.core.compat.create_integration.IPFlywheelStraddleClip
import qouteall.imm_ptl.core.compat.mixin_create.IPEmbeddedEnvironmentAccessor
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleClientSessions
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleRender
import java.util.BitSet
import java.util.WeakHashMap

/** Pass-local light data shared by Flywheel's instancing and indirect backends. */
object TrainPortalFlywheelLighting {
    private val sections = SableFlywheelLightStorage::class.java.getDeclaredField("scene2SectionArenaIndexMap")
        .also { it.isAccessible = true }
    private val changed = SableFlywheelLightStorage::class.java.getDeclaredField("changed").also { it.isAccessible = true }
    private val updateSections = SableFlywheelLightStorage::class.java.getDeclaredMethod("updateLightSections").also { it.isAccessible = true }
    private val environments = EnvironmentStorage::class.java.getDeclaredField("environments").also { it.isAccessible = true }
    private val farHalf = IPFlywheelStraddleClip::class.java.getDeclaredField("farHalf").also { it.isAccessible = true }
    private val shipFarHalf = Class.forName("qouteall.imm_ptl.core.compat.create_integration.IPFlywheelStraddleClip\$ShipFarHalf")
    private val farShip = shipFarHalf.getDeclaredMethod("ship").also { it.isAccessible = true }
    private val farSession = shipFarHalf.getDeclaredMethod("session").also { it.isAccessible = true }
    private val scene = EmbeddedEnvironment::class.java.getDeclaredField("sable\$sceneId").also { it.isAccessible = true }
    private val flatInstances = BaseInstancer::class.java.getDeclaredField("instances").also { it.isAccessible = true }
    private val pages = IndirectInstancer::class.java.getDeclaredField("pages").also { it.isAccessible = true }
    private val pageInstances = Class.forName("dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer\$InstancePage")
        .getDeclaredField("instances").also { it.isAccessible = true }

    private data class Lighting(val ship: ClientSubLevel, val level: ClientLevel, val pose: Pose3dc, val projected: Boolean)
    private data class Patch(val pointer: Long, val index: Int, val original: ByteArray)
    private data class MatrixPatch(val pointer: Long, val original: Float)
    private data class InstancePatch(val instance: ColoredLitInstance, val original: Int)
    private data class SectionKey(val ship: java.util.UUID, val dimension: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
        val section: Long, val originalHash: Int, val pose: List<Double>)
    private data class RelitSection(val bytes: ByteArray, val sample: Int, val originalSample: Int)
    private class Cache(var tick: Long = Long.MIN_VALUE) {
        val sections = LinkedHashMap<SectionKey, RelitSection>()
    }
    private class Pass(val scenes: Set<Int>, val environmentStorage: EnvironmentStorage)
    private class Requests {
        var native = LongOpenHashSet()
        var revision = 0L
        var appliedRevision = -1L
        var added = LongOpenHashSet()
    }
    private var pass: Pass? = null
    private val draws = WeakHashMap<ClientSubLevel, TrainPortalClientLighting.DrawLighting>()
    private val caches = WeakHashMap<SableFlywheelLightStorage, Cache>()
    private val instanceSkyClears = WeakHashMap<ClientSubLevel, Int>()
    private val requests = WeakHashMap<SableFlywheelLightStorage, Requests>()
    private val internalRequest = ThreadLocal.withInitial { false }
    private var backend: String? = null

    @JvmStatic fun lastDraw(ship: ClientSubLevel): TrainPortalClientLighting.DrawLighting? = draws[ship]
    @JvmStatic fun lastInstanceSkyClears(ship: ClientSubLevel): Int = instanceSkyClears[ship] ?: 0
    @JvmStatic fun lastBackend(): String? = backend

    /** Flywheel invokes this from its frame executor; never traverse sessions from that worker. */
    fun rememberSections(storage: SableFlywheelLightStorage, sections: LongSet) {
        if (internalRequest.get()) return
        synchronized(requests) {
            val state = requests.getOrPut(storage) { Requests() }
            state.native = LongOpenHashSet(sections)
            state.revision++
        }
    }

    private fun requirePlotSections(storage: SableFlywheelLightStorage) {
        val required = LongOpenHashSet()
        val level = storage.level() as? ClientLevel ?: return
        val container = SubLevelContainer.getContainer(level) ?: return
        // Keep all active source plots requested across the several projections
        // of this manager, avoiding allocation/LUT churn between those passes.
        for (ship in container.allSubLevels) {
            if (IPStraddleClientSessions.renderSessionsOf(ship).isEmpty()) continue
            val box = ship.plot.boundingBox
            if (box.minX() > box.maxX() || box.minY() > box.maxY() || box.minZ() > box.maxZ()) continue
            for (x in (box.minX() shr 4)..(box.maxX() shr 4))
                for (y in (box.minY() shr 4)..(box.maxY() shr 4))
                    for (z in (box.minZ() shr 4)..(box.maxZ() shr 4)) required.add(SectionPos.asLong(x, y, z))
        }
        val augmented = synchronized(requests) {
            val state = requests.getOrPut(storage) { Requests() }
            if (required == state.added && state.revision == state.appliedRevision) return
            // With no previous augmentation the native frame plan already did this work.
            if (required.isEmpty() && state.added.isEmpty()) {
                state.appliedRevision = state.revision
                return
            }
            state.added = required
            state.appliedRevision = state.revision
            LongOpenHashSet(state.native).also { it.addAll(required) }
        }
        internalRequest.set(true)
        try {
            storage.sections(augmented)
            // Kinetic visuals only implement LightUpdatedVisual, so their plot
            // sections never reach Flywheel's ShaderLightVisual request collector.
            // Use Sable's allocator/LUT/cleanup rather than patching world scene0.
            updateSections.invoke(storage)
        } finally {
            internalRequest.remove()
        }
    }

    /** Called after Sable's setupDraw injection, so its source sky multiplier cannot undo relit data. */
    @JvmStatic fun relitEnvironment(environment: EmbeddedEnvironment): Boolean {
        val current = pass ?: return false
        return (environment as IPEmbeddedEnvironmentAccessor).ip_getEngine().environmentStorage() === current.environmentStorage &&
            scene.getInt(environment) in current.scenes
    }

    private fun lighting(context: RenderContext): Map<Int, Lighting> {
        val container = SubLevelContainer.getContainer(context.level()) ?: return emptyMap()
        val far = farHalf.get(null)
        if (far != null) {
            // A Create carriage has another far-half record. Leave that unrelated path alone.
            if (!shipFarHalf.isInstance(far)) return emptyMap()
            val ship = farShip.invoke(far) as ClientSubLevel
            val session = farSession.invoke(far) as IPStraddleClientSessions.Session
            val target = ClientWorldLoader.getOptionalWorld(session.destDim()) ?: return emptyMap()
            val id = container.getLightingSceneId(ship)
            if (id < 0) return emptyMap()
            return mapOf(id to Lighting(ship, target, session.isometry().mapPose(IPStraddleRender.realRenderPose(ship)), true))
        }
        return container.allSubLevels.filterIsInstance<ClientSubLevel>().mapNotNull { ship ->
            if (IPStraddleClientSessions.renderSessionsOf(ship).isEmpty()) return@mapNotNull null
            val id = container.getLightingSceneId(ship)
            if (id < 0) null else id to Lighting(ship, ship.level, IPStraddleRender.realRenderPose(ship), false)
        }.toMap()
    }

    /**
     * EngineImpl has already flushed its environment matrices. Rewrite only the
     * selected scenes before DrawManager uploads light/matrices, then restore CPU
     * originals in finally. Marking restored sections dirty forces the next draw
     * to restore their GPU copy too, including when the session just ended.
     */
    @Suppress("UNCHECKED_CAST")
    fun withPass(engine: EngineImpl, context: RenderContext, render: () -> Unit) {
        val storage = engine.lightStorage() as? SableFlywheelLightStorage ?: return render()
        val selected = lighting(context)
        requirePlotSections(storage)
        if (selected.isEmpty()) return render()
        val map = sections.get(storage) as Int2ObjectMap<Long2IntMap>
        val dirty = changed.get(storage) as BitSet
        val patched = mutableListOf<Patch>()
        val matrices = mutableListOf<MatrixPatch>()
        val instancePatches = mutableListOf<InstancePatch>()
        val previous = pass
        val env = engine.environmentStorage()
        val cache = caches.getOrPut(storage) { Cache() }
        if (cache.tick != context.level().gameTime) {
            cache.sections.clear()
            cache.tick = context.level().gameTime
        }
        try {
            for ((id, light) in selected) {
                val bySection = map[id] ?: continue
                var brightest = -1
                var sample = 0
                var originalSample = 0
                for (entry in bySection.long2IntEntrySet()) {
                    val pointer = storage.arena.indexToPointer(entry.intValue) + LightStorage.SOLID_SIZE_BYTES
                    val bytes = ByteArray(LightStorage.LIGHT_SIZE_BYTES)
                    MemoryUtil.memByteBuffer(pointer, bytes.size).get(bytes)
                    patched.add(Patch(pointer, entry.intValue, bytes))
                    val p = light.pose.position(); val q = light.pose.orientation(); val r = light.pose.rotationPoint()
                    val key = SectionKey(light.ship.uniqueId, light.level.dimension(), entry.longKey, bytes.contentHashCode(),
                        listOf(p.x(), p.y(), p.z(), q.x(), q.y(), q.z(), q.w(), r.x(), r.y(), r.z()))
                    val relit = cache.sections[key] ?: relight(light, entry.longKey, bytes).also {
                        if (cache.sections.size >= 256) cache.sections.remove(cache.sections.keys.first())
                        cache.sections[key] = it
                    }
                    MemoryUtil.memByteBuffer(pointer, bytes.size).put(relit.bytes)
                    val brightness = maxOf(LightTexture.block(relit.sample), LightTexture.sky(relit.sample))
                    if (brightness > brightest) { brightest = brightness; sample = relit.sample; originalSample = relit.originalSample }
                    dirty.set(entry.intValue)
                }
                if (brightest >= 0) draws[light.ship] = TrainPortalClientLighting.DrawLighting(light.level.dimension(),
                    sample, originalSample, light.projected, bySection.size * LightStorage.BLOCKS_PER_SECTION)
            }
            pass = Pass(selected.keys, env)
            backend = engine.drawManager().javaClass.simpleName
            selected.values.forEach { instanceSkyClears[it.ship] = 0 }
            for (instancer in engine.drawManager().instancers().values) {
                val environment = instancer.environment as? EmbeddedEnvironment ?: continue
                if (!relitEnvironment(environment)) continue
                val ship = selected[scene.getInt(environment)]?.ship ?: continue
                for (instance in instances(instancer)) {
                    if (instance.light ushr 16 == 0) continue
                    // The shader maxes scene lighting with per-instance light. A
                    // source-world sky value must not override target Nether sky0.
                    instancePatches.add(InstancePatch(instance, instance.light))
                    instance.light = instance.light and 0xffff
                    instance.setChanged()
                    instanceSkyClears[ship] = (instanceSkyClears[ship] ?: 0) + 1
                }
            }
            for (environment in environments.get(env) as Iterable<EmbeddedEnvironment>) {
                if (environment.isDeleted || !relitEnvironment(environment)) continue
                // Sable's pinned matrix extension stores sky scale at byte112.
                val pointer = env.arena.indexToPointer(environment.matrixIndex) + 112L
                matrices.add(MatrixPatch(pointer, MemoryUtil.memGetFloat(pointer)))
                MemoryUtil.memPutFloat(pointer, 1.0f)
            }
            render()
        } finally {
            for (patch in patched) {
                MemoryUtil.memByteBuffer(patch.pointer, patch.original.size).put(patch.original)
                dirty.set(patch.index)
            }
            for (matrix in matrices) MemoryUtil.memPutFloat(matrix.pointer, matrix.original)
            for (instance in instancePatches) {
                instance.instance.light = instance.original
                instance.instance.setChanged()
            }
            pass = previous
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun instances(instancer: AbstractInstancer<*>): Sequence<ColoredLitInstance> = when (instancer) {
        is BaseInstancer<*> -> (flatInstances.get(instancer) as Iterable<*>).asSequence().filterIsInstance<ColoredLitInstance>()
        is IndirectInstancer<*> -> ((pages.get(instancer) as java.util.concurrent.atomic.AtomicReference<*>).get() as Array<*>)
            .asSequence().filterNotNull().flatMap { (pageInstances.get(it) as Array<*>).asSequence() }
            .filterIsInstance<ColoredLitInstance>()
        else -> emptySequence()
    }

    private fun relight(light: Lighting, sectionId: Long, original: ByteArray): RelitSection {
        val bytes = original.copyOf()
        val section = SectionPos.of(sectionId)
        var offset = 0
        var brightest = -1
        var sample = 0
        var originalSample = 0
        for (y in -1..16) for (z in -1..16) for (x in -1..16) {
            val before = original[offset].toInt() and 255
            val originalPacked = LightTexture.pack(before and 15, before ushr 4)
            val local = Vec3(section.minBlockX() + x + .5, section.minBlockY() + y + .5, section.minBlockZ() + z + .5)
            val packed = TrainPortalClientLighting.samplePackedLightIn(light.ship, local, light.level, light.pose, originalPacked)
            bytes[offset] = (LightTexture.block(packed) or (LightTexture.sky(packed) shl 4)).toByte()
            val brightness = maxOf(LightTexture.block(packed), LightTexture.sky(packed))
            if (brightness > brightest) { brightest = brightness; sample = packed; originalSample = originalPacked }
            offset++
        }
        return RelitSection(bytes, sample, originalSample)
    }
}
