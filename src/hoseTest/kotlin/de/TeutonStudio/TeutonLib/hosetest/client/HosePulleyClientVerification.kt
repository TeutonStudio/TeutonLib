package de.TeutonStudio.TeutonLib.hosetest.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.simibubi.create.content.contraptions.pulley.AbstractPulleyVisual
import com.simibubi.create.content.contraptions.pulley.HosePulleyVisual
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import de.TeutonStudio.TeutonLib.integration.hose.HosePulleyHost
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import dev.engine_room.flywheel.backend.engine.BaseInstancer
import dev.engine_room.flywheel.backend.engine.InstanceHandleImpl
import dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer
import dev.engine_room.flywheel.backend.engine.indirect.ObjectStorage
import dev.engine_room.flywheel.backend.util.AtomicBitSet
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.visual.util.InstanceRecycler
import dev.engine_room.flywheel.lib.visual.util.SmartRecycler
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import org.lwjgl.opengl.GL11
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/** Opt-in captures of the actual server-controlled pulley; the probe constructs no visual. */
@EventBusSubscriber(modid = "teutonlib_hose_test", value = [Dist.CLIENT])
object HosePulleyClientVerification {
    private val enabled = java.lang.Boolean.getBoolean("teutonlib.hosePulleyClientTests")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val log = LoggerFactory.getLogger("TeutonLib/HosePulleyClientTest")
    private val origin = BlockPos(8, 220, 8)
    private data class Capture(val name: String, val physical: Boolean, var wait: Int = 60)
    private var pending: Capture? = null
    private var initialized = false
    private var ready = false
    private var finished = false
    private var finishing = false
    private var renders = 0
    private var nativeDraws = 0
    private var fixtureVisual: Any? = null
    private val captures = JsonArray()
    private var applied = JsonObject()
    private var nativeObservation: JsonObject? = null

    private fun directory() = Minecraft.getInstance().gameDirectory.toPath().resolve("hose-pulley-verification")

    @JvmStatic
    fun recordRender(entity: HoseHeadEntity) {
        if (enabled && (pending?.wait ?: -1) in 0..30 && entity.isAlive && entity.ownerPosition == origin) renders++
    }

    @JvmStatic
    fun recordNativeDraw(position: BlockPos) {
        if (enabled && (pending?.wait ?: -1) in 0..30 && position == origin) nativeDraws++
    }

    @SubscribeEvent
    @JvmStatic
    fun message(event: ClientChatReceivedEvent.System) {
        if (!enabled || finished) return
        try {
            val message = event.message.string
            when {
                message.startsWith("TEUTONLIB_HOSE_CAPTURE ") -> {
                    check(pending == null) { "Previous hose capture is pending" }
                    val parts = message.removePrefix("TEUTONLIB_HOSE_CAPTURE ").split(' ')
                    require(parts.size == 2 && parts[0].matches(Regex("[a-z0-9-]{1,64}")))
                    require(parts[1] == "true" || parts[1] == "false")
                    pending = Capture(parts[0], parts[1].toBooleanStrict())
                    renders = 0; nativeDraws = 0
                    event.isCanceled = true
                }
                message == "TEUTONLIB_HOSE_FINISH" -> {
                    finishing = true
                    event.isCanceled = true
                }
            }
        } catch (failure: Throwable) { fail(failure) }
    }

    @SubscribeEvent
    @JvmStatic
    fun tick(event: ClientTickEvent.Post) {
        if (!enabled || finished) return
        val mc = Minecraft.getInstance()
        try {
            if (!initialized) {
                Files.createDirectories(directory())
                applied = JsonObject()
                for (name in listOf("com.simibubi.create.content.contraptions.pulley.AbstractPulleyRenderer",
                    "com.simibubi.create.content.contraptions.pulley.AbstractPulleyVisual")) {
                    check(Class.forName(name).declaredMethods.any { it.name.contains("teutonlib\$replaceNativeHose") }) {
                        "Client mixin did not apply to $name"
                    }
                    applied.addProperty(name, true)
                }
                mc.window.setWindowed(1280, 800)
                mc.options.hideGui = true
                mc.options.pauseOnLostFocus = false
                mc.options.renderDistance().set(8)
                mc.options.framerateLimit().set(60)
                mc.options.enableVsync().set(false)
                initialized = true
            }
            if (mc.level != null && mc.player != null && mc.screen == null && mc.overlay == null) {
                if (!ready) {
                    Files.writeString(directory().resolve("ready.json"), gson.toJson(JsonObject().also {
                        it.addProperty("player", mc.player!!.gameProfile.name)
                        it.add("appliedMixins", applied)
                    }))
                    ready = true
                }
                pending?.let { if (it.wait > 0) it.wait-- }
            }
            if (finishing && pending == null) {
                check(captures.size() >= 3) { "Live physical/native/physical captures are required" }
                Files.writeString(directory().resolve("report.json"), gson.toJson(JsonObject().also {
                    it.addProperty("passed", true)
                    it.add("captures", captures)
                    it.add("appliedMixins", applied)
                    it.addProperty("limits", "Only the selected Flywheel or BER backend executes native draws; both mixins are transformed.")
                }))
                finished = true
                mc.stop()
            }
        } catch (failure: Throwable) { fail(failure) }
    }

    private fun field(type: Class<*>, instance: Any, name: String): Any? =
        type.getDeclaredField(name).also { it.isAccessible = true }.get(instance)

    /** isVisible() in Flywheel 1.0.6 misses INDIRECT's InstancePage states; inspect the live slot. */
    private fun instanceState(instance: TransformedInstance): JsonObject {
        val handle = instance.handle() as InstanceHandleImpl<*>
        val state = checkNotNull(handle.state)
        val indirect = IndirectInstancer.fromState(state)
        return JsonObject().also { result ->
            result.addProperty("state", state.javaClass.name)
            result.addProperty("index", handle.index)
            result.addProperty("apiVisible", handle.isVisible)
            result.addProperty("hidden", state is InstanceHandleImpl.Hidden<*>)
            val active = when {
                indirect != null -> {
                    val pageNo = field(state.javaClass, state, "pageNo") as Int
                    val instances = field(state.javaClass, state, "instances") as Array<*>
                    val handles = field(state.javaClass, state, "handles") as Array<*>
                    val valid = (field(state.javaClass, state, "valid") as AtomicInteger).get()
                    val pages = (field(IndirectInstancer::class.java, indirect, "pages") as AtomicReference<*>).get() as Array<*>
                    val localIndex = handle.index and ObjectStorage.PAGE_MASK
                    val belongsToPage = handle.index >= 0 &&
                        handle.index ushr ObjectStorage.LOG_2_PAGE_SIZE == pageNo &&
                        pages.getOrNull(pageNo) === state
                    val validSlot = valid and (1 shl localIndex) != 0
                    val sameInstance = instances[localIndex] === instance
                    val sameHandle = handles[localIndex] === handle
                    result.addProperty("page", pageNo)
                    result.addProperty("localIndex", localIndex)
                    result.addProperty("pageRegistered", belongsToPage)
                    result.addProperty("slotValid", validSlot)
                    result.addProperty("sameInstance", sameInstance)
                    result.addProperty("sameHandle", sameHandle)
                    belongsToPage && validSlot && sameInstance && sameHandle
                }
                state is BaseInstancer<*> -> {
                    val instances = field(BaseInstancer::class.java, state, "instances") as List<*>
                    val handles = field(BaseInstancer::class.java, state, "handles") as List<*>
                    val deleted = field(BaseInstancer::class.java, state, "deleted") as AtomicBitSet
                    handle.index >= 0 && instances.getOrNull(handle.index) === instance &&
                        handles.getOrNull(handle.index) === handle && !deleted.get(handle.index)
                }
                else -> false
            }
            result.addProperty("activeSlot", active)
        }
    }

    /** Observe existing instances after rendering, without calling animate or rebuilding the visual. */
    private fun nativeVisual(physical: Boolean): JsonObject {
        val level = checkNotNull(Minecraft.getInstance().level)
        val visualized = VisualizationManager.supportsVisualization(level)
        return JsonObject().also { result ->
            nativeObservation = result
            result.addProperty("backend", if (visualized) "Flywheel" else "BER")
            result.addProperty("nativeBerDraws", nativeDraws)
            if (!visualized) {
                check(if (physical) nativeDraws == 0 else nativeDraws > 0) { "Native BER suppression did not follow the server flag" }
                return@also
            }
            val manager = checkNotNull(VisualizationManager.get(level))
            val storage = (manager.blockEntities() as VisualManagerImpl<*, *>).storage as BlockEntityStorage
            val visual = checkNotNull(storage.visualAtPos(origin.asLong())) { "Fixture has no existing Flywheel visual" }
            check(visual is HosePulleyVisual) { "Unexpected fixture visual ${visual.javaClass.name}" }
            if (fixtureVisual == null) fixtureVisual = visual
            check(visual === fixtureVisual) { "Mode change recreated the visual instead of restoring its existing instances" }
            val magnet = field(AbstractPulleyVisual::class.java, visual, "magnet") as TransformedInstance
            val rope = field(AbstractPulleyVisual::class.java, visual, "rope") as SmartRecycler<*, *>
            val recyclers = field(SmartRecycler::class.java, rope, "recyclers") as Map<*, *>
            val ropes = recyclers.values.flatMap {
                (field(InstanceRecycler::class.java, checkNotNull(it), "instances") as List<*>).map { instance ->
                    instanceState(instance as TransformedInstance)
                }
            }
            val magnetState = instanceState(magnet)
            val visible = magnetState["activeSlot"].asBoolean
            result.addProperty("sameVisual", true)
            result.addProperty("visualIdentity", System.identityHashCode(visual))
            result.addProperty("magnetVisible", visible)
            result.add("magnetState", magnetState)
            result.addProperty("ropeInstances", ropes.size)
            result.add("ropeStates", JsonArray().also { array -> ropes.forEach(array::add) })
            check(visible != physical && (if (physical) ropes.isEmpty() else ropes.isNotEmpty())) {
                "Existing Flywheel instances did not follow server flag: physical=$physical observation=$result"
            }
            check(!physical || magnetState["hidden"].asBoolean) { "Suppressed magnet lost its reusable Hidden state: $result" }
            check(ropes.all { it["activeSlot"].asBoolean }) { "Native ropes contain inactive instance slots: $result" }
            check(nativeDraws == 0) { "BER also drew while Flywheel was active" }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun frame(event: RenderFrameEvent.Post) {
        if (!enabled || finished) return
        val capture = pending ?: return
        if (capture.wait != 0) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        if (mc.player == null || mc.screen != null || mc.overlay != null) return
        try {
            val host = level.getBlockEntity(origin) as? HosePulleyHost ?: error("Fixture pulley not loaded")
            check(host.physicalHeadEnabled == capture.physical) { "Server-controlled owner flag was not synchronized" }
            val heads = level.getEntitiesOfClass(HoseHeadEntity::class.java, AABB(origin).inflate(32.0)) {
                it.isAlive && it.ownerPosition == origin
            }
            check(heads.size == (if (capture.physical) 1 else 0)) { "Wrong physical head count after ${capture.name}: ${heads.size}" }
            check(if (capture.physical) renders > 0 else renders == 0) { "Physical head renders did not follow the owner flag" }
            if (capture.name == "connector-seated") {
                val head = heads.single()
                val socket = de.TeutonStudio.TeutonLib.integration.hose.HoseDrainConnection.get(
                    level.getBlockEntity(head.dockPosition ?: error("Dock position not synchronized"))) ?: error("Drain not loaded on client")
                check(socket.identity == head.dockId && socket.headId == head.uuid) { "Socket/head UUIDs not synchronized" }
                val model = mc.blockRenderer.blockModelShaper.getBlockModel(socket.blockState)
                check(model is de.TeutonStudio.TeutonLib.client.hose.HoseDrainModel) { "Drain model wrapper is missing" }
                val data = model.getModelData(level, socket.blockPos, socket.blockState, net.neoforged.neoforge.client.model.data.ModelData.EMPTY)
                check(data.get(de.TeutonStudio.TeutonLib.client.hose.HoseDrainModel.LOCKED) == true) { "Drain did not select the locked model" }
                check(kotlin.math.abs(head.position().distanceTo(socket.socketWorld())) < .05) { "Client head does not sit in drain" }
            }
            val cable = heads.singleOrNull()?.let { de.TeutonStudio.TeutonLib.client.hose.HoseCableClient.points(it.uuid) }.orEmpty()
            if (capture.physical) {
                check(cable.size >= 2) { "Client has no synchronized cable route" }
                check(de.TeutonStudio.TeutonLib.client.hose.HoseCableClient.wasRendered(heads.single().uuid)) { "Cable world renderer did not draw the physical head and strand" }
            }
            val report = JsonObject().also {
                it.addProperty("passed", true)
                it.addProperty("name", capture.name)
                it.addProperty("physicalHeadEnabled", host.physicalHeadEnabled)
                it.addProperty("headCount", heads.size)
                it.addProperty("docked", heads.singleOrNull()?.dockId != null)
                heads.singleOrNull()?.let { head -> it.addProperty("headUuid", head.uuid.toString()) }
                it.addProperty("headRenderInvocations", renders)
                it.addProperty("cablePoints", cable.size)
                it.add("native", nativeVisual(capture.physical))
                it.add("appliedMixins", applied)
                it.addProperty("renderer", GL11.glGetString(GL11.GL_RENDERER))
                it.addProperty("opengl", GL11.glGetString(GL11.GL_VERSION))
            }
            capture.wait = -1
            Screenshot.grab(mc.gameDirectory, "hose-pulley-${capture.name}.png", mc.mainRenderTarget,
                Consumer { _: Component? -> mc.execute {
                    try {
                        val path = mc.gameDirectory.toPath().resolve("screenshots/hose-pulley-${capture.name}.png")
                        check(Files.isRegularFile(path))
                        report.addProperty("screenshot", path.toAbsolutePath().toString())
                        Files.writeString(directory().resolve("${capture.name}.json"), gson.toJson(report))
                        captures.add(report)
                        pending = null
                        log.info("Hose pulley client captured {}", capture.name)
                    } catch (failure: Throwable) { fail(failure) }
                } })
        } catch (failure: Throwable) { fail(failure) }
    }

    private fun fail(failure: Throwable) {
        log.error("Hose pulley client verification FAIL", failure)
        runCatching {
            Files.createDirectories(directory())
            Files.writeString(directory().resolve("report.json"), gson.toJson(JsonObject().also {
                it.addProperty("passed", false)
                it.addProperty("failure", failure.stackTraceToString())
                nativeObservation?.let { observation -> it.add("nativeObservation", observation) }
                it.add("captures", captures)
            }))
        }
        finished = true
        Minecraft.getInstance().stop()
    }
}
