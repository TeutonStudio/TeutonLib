package de.TeutonStudio.TeutonLib.hosetest.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import de.TeutonStudio.TeutonLib.client.hose.HoseCableClient
import de.TeutonStudio.TeutonLib.integration.energy.FluxPointConnections
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.util.RandomSource
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import java.nio.file.Files
import java.util.UUID
import java.util.function.Consumer

@EventBusSubscriber(modid = "teutonlib_hose_test", value = [Dist.CLIENT])
object HoseCableClientVerification {
    private val enabled = java.lang.Boolean.getBoolean("teutonlib.hoseCableClientTests")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private data class Capture(val name: String, val id: UUID, val tracked: Boolean, var ticks: Int = 30)
    private var pending: Capture? = null
    private var ready = false
    private fun directory() = Minecraft.getInstance().gameDirectory.toPath().resolve("cable-verification")
    @SubscribeEvent @JvmStatic fun message(event: ClientChatReceivedEvent.System) {
        if (!enabled) return
        val text = event.message.string
        if (text == "TEUTONLIB_CABLE_FINISH") { event.isCanceled=true; Minecraft.getInstance().stop(); return }
        if (!text.startsWith("TEUTONLIB_CABLE_CAPTURE ")) return
        val parts = text.removePrefix("TEUTONLIB_CABLE_CAPTURE ").split(' ')
        require(parts.size == 3 && parts[0].matches(Regex("[a-z-]+")))
        pending=Capture(parts[0],UUID.fromString(parts[1]),parts[2].toBooleanStrict()); event.isCanceled=true
    }
    @SubscribeEvent @JvmStatic fun tick(event: ClientTickEvent.Post) {
        if (!enabled) return
        val mc=Minecraft.getInstance()
        if (mc.level == null || mc.player == null || mc.screen != null || mc.overlay != null) return
        if (!ready) {
            Files.createDirectories(directory())
            mc.window.setWindowed(1280,800); mc.options.hideGui=true; mc.options.pauseOnLostFocus=false
            mc.options.renderDistance().set(8); mc.options.framerateLimit().set(40); mc.options.enableVsync().set(false)
            Files.writeString(directory().resolve("ready.json"),gson.toJson(JsonObject().also { it.addProperty("player",mc.player!!.gameProfile.name) }))
            ready=true
        }
        pending?.let { if (it.ticks > 0) it.ticks-- }
    }
    @SubscribeEvent @JvmStatic fun frame(event: RenderFrameEvent.Post) {
        if (!enabled) return
        val capture=pending?.takeIf { it.ticks == 0 } ?: return
        val mc=Minecraft.getInstance(); val world=mc.level ?: return
        if (mc.screen != null || mc.overlay != null) return
        val result=JsonObject()
        try {
            val points=HoseCableClient.points(capture.id)
            check(points.size >= 2) { "No synchronized strand ${capture.id}" }
            check(HoseCableClient.wasRendered(capture.id)) { "World renderer culled the visible strand" }
            val tracked=world.entitiesForRendering().any { it.uuid == capture.id }
            check(tracked == capture.tracked) { "Expected entity tracking ${capture.tracked}, got $tracked" }
            if (capture.name == "flux") {
                val pos=BlockPos(44,213,43); val state=world.getBlockState(pos)
                check(state.getValue(FluxPointConnections.REAR.getValue(Direction.WEST))) { "Rear state did not synchronize" }
                val model=mc.blockRenderer.blockModelShaper.getBlockModel(state)
                val quads=model.getQuads(state,null,RandomSource.create())
                val minimum=quads.minOf { quad -> (0..3).minOf { Float.fromBits(quad.vertices[it*(quad.vertices.size/4)]) } }
                check(kotlin.math.abs(minimum+.125f)<1e-6) { "Baked model has wrong rear extent: $minimum" }
                result.addProperty("rearModelMinX",minimum)
            }
            result.addProperty("passed",true); result.addProperty("name",capture.name); result.addProperty("tracked",tracked)
            result.addProperty("head",capture.id.toString())
            result.add("points",JsonArray().also { array -> points.forEach { p -> array.add(JsonArray().also { it.add(p.x); it.add(p.y); it.add(p.z) }) } })
            capture.ticks=-1
            Screenshot.grab(mc.gameDirectory,"cable-${capture.name}.png",mc.mainRenderTarget,Consumer { _: Component? -> mc.execute {
                result.addProperty("screenshot",mc.gameDirectory.toPath().resolve("screenshots/cable-${capture.name}.png").toAbsolutePath().toString())
                Files.writeString(directory().resolve("${capture.name}.json"),gson.toJson(result)); pending=null
            } })
        } catch (failure: Throwable) {
            result.addProperty("passed",false); result.addProperty("failure",failure.toString())
            Files.writeString(directory().resolve("${capture.name}.json"),gson.toJson(result)); pending=null
        }
    }
}
