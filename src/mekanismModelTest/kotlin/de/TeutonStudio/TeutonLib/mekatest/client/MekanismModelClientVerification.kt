package de.TeutonStudio.TeutonLib.mekatest.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import mekanism.common.tile.transmitter.TileEntityTransmitter
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderer
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import org.lwjgl.opengl.GL11
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

/** Captures the real client tile, model data and section compilation in the opt-in test world. */
@EventBusSubscriber(modid = "teutonlib_mekanism_model_test", value = [Dist.CLIENT])
object MekanismModelClientVerification {
    private val enabled = java.lang.Boolean.getBoolean("teutonlib.mekanismModelClientTests")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val log = LoggerFactory.getLogger("TeutonLib/MekanismModelClientTest")
    private data class Capture(val name: String, var wait: Int = 40)
    private var state: JsonObject? = null
    private var capture: Capture? = null
    private val captures = JsonArray()
    private var initialized = false
    private var ready = false
    private var finishing = false
    private var finished = false

    private fun directory(): Path = Minecraft.getInstance().gameDirectory.toPath().resolve("mekanism-model-verification")

    @SubscribeEvent
    @JvmStatic
    fun message(event: ClientChatReceivedEvent.System) {
        if (!enabled || finished) return
        try {
            val message = event.message.string
            when {
                message.startsWith("TEUTONLIB_MEKANISM_STATE ") -> {
                    state = JsonParser.parseString(message.removePrefix("TEUTONLIB_MEKANISM_STATE ")).asJsonObject
                    event.isCanceled = true
                }
                message.startsWith("TEUTONLIB_MEKANISM_CAPTURE ") -> {
                    check(capture == null) { "Previous capture is pending" }
                    val name = message.removePrefix("TEUTONLIB_MEKANISM_CAPTURE ")
                    require(name.matches(Regex("[a-z0-9-]{1,64}")))
                    check(state != null) { "No server fixture state was received" }
                    capture = Capture(name)
                    event.isCanceled = true
                }
                message == "TEUTONLIB_MEKANISM_FINISH" -> {
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
                mc.window.setWindowed(1280, 800)
                mc.options.hideGui = true
                mc.options.pauseOnLostFocus = false
                mc.options.renderDistance().set(8)
                mc.options.framerateLimit().set(120)
                mc.options.enableVsync().set(false)
                initialized = true
            }
            if (mc.level != null && mc.player != null && mc.screen == null && mc.overlay == null) {
                if (!ready) {
                    Files.writeString(directory().resolve("ready.json"), gson.toJson(JsonObject().also {
                        it.addProperty("player", mc.player!!.gameProfile.name)
                        it.addProperty("dimension", mc.level!!.dimension().location().toString())
                    }))
                    ready = true
                }
                capture?.let { if (it.wait > 0) it.wait-- }
            }
            if (finishing && capture == null) {
                check(captures.size() >= 4) { "World, converted, static mode-change and moving mode-change captures are required" }
                Files.writeString(directory().resolve("report.json"), gson.toJson(JsonObject().also {
                    it.addProperty("passed", captures.all { stage -> stage.asJsonObject.get("passed").asBoolean })
                    it.add("captures", captures)
                }))
                log.info("Mekanism client models PASS: {} stages", captures.size())
                finished = true
                mc.stop()
            }
        } catch (failure: Throwable) { fail(failure) }
    }

    @SubscribeEvent
    @JvmStatic
    fun frame(event: RenderFrameEvent.Post) {
        if (!enabled || finished) return
        val pending = capture ?: return
        if (pending.wait != 0) return
        try {
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return
            if (mc.player == null || mc.screen != null || mc.overlay != null) return
            val fixture = checkNotNull(state)
            check(fixture.get("passed").asBoolean) { "Server fixture has not settled: $fixture" }
            fun position(key: String): BlockPos = fixture.getAsJsonArray(key).let {
                BlockPos(it[0].asInt, it[1].asInt, it[2].asInt)
            }
            val subject = if (fixture.has("mountedPipe")) position("mountedPipe") else position("sourcePipe")
            fun describe(position: BlockPos): JsonObject {
                val probe = MekanismModelProbe.snapshot(level, position)
                val tile = level.getBlockEntity(position) as? TileEntityTransmitter
                return probe.deepCopy().also {
                    it.add("position", JsonArray().also { coords ->
                        coords.add(position.x); coords.add(position.y); coords.add(position.z)
                    })
                    it.add("raw", JsonObject().also { modes ->
                        for (side in listOf(Direction.WEST, Direction.EAST)) modes.addProperty(side.name.lowercase(),
                            tile?.transmitter?.getConnectionTypeRaw(side)?.name)
                    })
                    it.add("effective", JsonObject().also { modes ->
                        for (side in listOf(Direction.WEST, Direction.EAST)) modes.addProperty(side.name.lowercase(),
                            tile?.transmitter?.getConnectionType(side)?.name)
                    })
                    fun aliases(key: String) = JsonObject().also { modes ->
                        for (side in listOf("WEST", "EAST")) modes.add(side.lowercase(), probe.getAsJsonObject(key).get(side))
                    }
                    it.add("tileModel", aliases("tileModes"))
                    it.add("managerModel", aliases("managerModes"))
                    it.add("probe", probe)
                }
            }
            val world = describe(position("controlPipe"))
            val mounted = describe(subject)
            val selectedRenderer = SubLevelRenderer::class.java.getDeclaredField("selected")
                .also { it.isAccessible = true }.get(null).toString()
            val dispatcherClass = SubLevelRenderer.getDispatcher().javaClass.name
            val sodiumActive = runCatching {
                Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod", false, javaClass.classLoader)
            }.isSuccess
            val backendMatches = selectedRenderer == "SODIUM_REACHAROUND" &&
                dispatcherClass.endsWith("ReachAroundSubLevelRenderDispatcher") && sodiumActive
            fun expected(tile: JsonObject, swapped: Boolean): Boolean {
                val west = if (swapped) "PUSH" else "PULL"
                val east = if (swapped) "PULL" else "PUSH"
                return listOf("raw", "effective", "tileModel", "managerModel").all { key ->
                    tile.getAsJsonObject(key).let {
                        it.get("west")?.takeUnless { value -> value.isJsonNull }?.asString == west &&
                            it.get("east")?.takeUnless { value -> value.isJsonNull }?.asString == east
                    }
                } && tile.get("passed").asBoolean
            }
            val snapshot = JsonObject().also {
                it.addProperty("name", pending.name)
                it.addProperty("stage", fixture.get("stage").asString)
                it.add("shipId", fixture.get("ship"))
                val mountedCompiled = !fixture.has("mountedPipe") ||
                    listOf("actualCompileObserved", "actualNonemptyUnculledObserved", "actualSpecialEndGeometryMatches")
                        .all { key -> mounted.get(key).asBoolean }
                it.addProperty("passed", expected(world, false) &&
                    expected(mounted, fixture.get("stage").asString == "SWAP_READY") && mountedCompiled && backendMatches)
                it.addProperty("dimension", level.dimension().location().toString())
                it.addProperty("selectedRenderer", selectedRenderer)
                it.addProperty("renderDispatcher", dispatcherClass)
                it.addProperty("sodiumActive", sodiumActive)
                it.addProperty("renderer", GL11.glGetString(GL11.GL_RENDERER))
                it.addProperty("opengl", GL11.glGetString(GL11.GL_VERSION))
                it.add("fixture", fixture.deepCopy())
                it.add("control", world)
                it.add("subject", mounted)
                it.add("world", world)
                it.add("mounted", mounted)
                it.add("camera", JsonObject().also { camera ->
                    camera.addProperty("x", mc.player!!.x)
                    camera.addProperty("y", mc.player!!.y)
                    camera.addProperty("z", mc.player!!.z)
                    camera.addProperty("yaw", mc.player!!.yRot)
                    camera.addProperty("pitch", mc.player!!.xRot)
                })
            }
            pending.wait = -1
            Screenshot.grab(mc.gameDirectory, "mekanism-model-${pending.name}.png", mc.mainRenderTarget,
                Consumer { _: Component? -> mc.execute {
                    try {
                        val screenshot = mc.gameDirectory.toPath().resolve("screenshots/mekanism-model-${pending.name}.png")
                        check(Files.isRegularFile(screenshot))
                        snapshot.addProperty("screenshot", screenshot.toAbsolutePath().toString())
                        Files.writeString(directory().resolve("${pending.name}.json"), gson.toJson(snapshot))
                        captures.add(snapshot)
                        capture = null
                        log.info("Mekanism client captured {}", pending.name)
                    } catch (failure: Throwable) { fail(failure) }
                } })
        } catch (failure: Throwable) { fail(failure) }
    }

    private fun fail(failure: Throwable) {
        log.error("Mekanism client models FAIL", failure)
        runCatching {
            Files.createDirectories(directory())
            Files.writeString(directory().resolve("report.json"), gson.toJson(JsonObject().also {
                it.addProperty("passed", false)
                it.addProperty("failure", failure.stackTraceToString())
                it.add("captures", captures)
            }))
        }
        finished = true
        Minecraft.getInstance().stop()
    }
}
