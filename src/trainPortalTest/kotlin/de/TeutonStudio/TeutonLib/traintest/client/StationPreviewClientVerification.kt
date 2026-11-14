package de.TeutonStudio.TeutonLib.traintest.client

import com.simibubi.create.content.trains.station.StationBlockEntity
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import de.TeutonStudio.TeutonLib.traintest.StationPreviewVerification
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import java.nio.file.Files

/** Reads the real StationRenderer predicate calls; sends real client use-item packets. */
@EventBusSubscriber(modid = "teutonlib_train_portal_test", value = [Dist.CLIENT])
object StationPreviewClientVerification {
    private val enabled = java.lang.Boolean.getBoolean("teutonlib.stationPreviewTests")
    private val rendered = mutableMapOf<BlockPos, MutableMap<Int, Boolean>>()
    private var pending: Pair<String, Int>? = null
    private var frames = 0
    private var camera: net.minecraft.world.entity.decoration.ArmorStand? = null

    @SubscribeEvent @JvmStatic fun before(event: RenderFrameEvent.Pre) {
        if (enabled) camera?.let { Minecraft.getInstance().setCameraEntity(it) }
    }

    private fun camera(i: Int) {
        val mc = Minecraft.getInstance()
        val direction = StationPreviewVerification.directions[i]
        val at = StationPreviewVerification.start(i)
        val eye = Vec3.atCenterOf(at.relative(direction, 7).relative(direction.clockWise, 12)).add(0.0, 10.0, 0.0)
        val look = Vec3.atCenterOf(at.relative(direction, 7)).add(0.0, .5, 0.0)
        val delta = look.subtract(eye)
        camera = net.minecraft.world.entity.decoration.ArmorStand(mc.level!!, eye.x, eye.y, eye.z).also {
            it.setPos(eye.x, eye.y - it.eyeHeight, eye.z)
            it.xo = it.x; it.yo = it.y; it.zo = it.z
            it.yRot = Math.toDegrees(kotlin.math.atan2(-delta.x, delta.z)).toFloat(); it.yRotO = it.yRot
            it.yHeadRot = it.yRot; it.yHeadRotO = it.yRot
            it.xRot = -Math.toDegrees(kotlin.math.atan2(delta.y, kotlin.math.sqrt(delta.x*delta.x+delta.z*delta.z))).toFloat()
            it.xRotO = it.xRot
            mc.setCameraEntity(it)
        }
    }
    fun record(station: StationBlockEntity, offset: Int, valid: Boolean) {
        if (enabled) rendered.getOrPut(station.blockPos.immutable()) { mutableMapOf() }[offset] = valid
    }
    private fun directory() = Minecraft.getInstance().gameDirectory.toPath().resolve("station-preview")
    @SubscribeEvent @JvmStatic fun message(event: ClientChatReceivedEvent.System) {
        if (!enabled || !event.message.string.startsWith("STATION_PREVIEW ")) return
        event.isCanceled = true
        val args = event.message.string.removePrefix("STATION_PREVIEW ").split(' ')
        val mc = Minecraft.getInstance()
        Files.createDirectories(directory())
        try {
            when (args[0]) {
                "capture" -> { pending = args[1] to args[2].toInt(); frames = 0; rendered.clear(); camera(args[2].toInt()) }
                "click" -> {
                    camera = null; mc.setCameraEntity(mc.player)
                    val i = args[1].toInt(); val offset = args[2].toInt()
                    val at = StationPreviewVerification.start(i).relative(StationPreviewVerification.directions[i], offset + 1)
                    val result = mc.gameMode!!.useItemOn(mc.player!!, InteractionHand.MAIN_HAND,
                        BlockHitResult(Vec3.atCenterOf(at).add(0.0, .1, 0.0), Direction.UP, at, false))
                    Files.writeString(directory().resolve("click-$i-$offset.txt"), result.toString())
                }
                "quit" -> mc.stop()
            }
        } catch (e: Throwable) { Files.writeString(directory().resolve("failure.txt"), e.stackTraceToString()) }
    }
    @SubscribeEvent @JvmStatic fun frame(event: RenderFrameEvent.Post) {
        if (!enabled) return
        val (label, i) = pending ?: return
        if (++frames < 15) return
        val mc = Minecraft.getInstance()
        try {
            val station = mc.level?.getBlockEntity(StationPreviewVerification.stationPos(i)) as? StationBlockEntity ?: return
            val offsets = rendered[station.blockPos] ?: return
            val field = StationBlockEntity::class.java.getDeclaredField("bogeyLocations").also { it.isAccessible = true }
            val locations = (field.get(station) as IntArray).filter { it >= 0 }
            val inversions = StationBlockEntity::class.java.getDeclaredField("upsideDownBogeys").also { it.isAccessible = true }.get(station) as BooleanArray
            val direction = station.assemblyDirection
            val bogeys = locations.mapIndexed { index, offset ->
                val track = station.edgePoint.globalPosition.relative(direction, offset + 1)
                val at = if (inversions[index]) track.below() else track.above()
                val state = StationBuildRuntime.state(station, mc.level!!, at)
                mapOf("offset" to offset, "world" to listOf(at.x,at.y,at.z), "state" to state.toString())
            }
            val data = mapOf("camera" to mapOf("position" to camera?.position().toString(), "yaw" to camera?.yRot, "pitch" to camera?.xRot), "case" to i, "direction" to direction.name, "bogeys" to bogeys, "renderedValid" to offsets.toSortedMap())
            Screenshot.grab(mc.gameDirectory, "station-$label.png", mc.mainRenderTarget) {}
            Files.writeString(directory().resolve("$label.json"), com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data))
            pending = null
        } catch (e: Throwable) { Files.writeString(directory().resolve("failure.txt"), e.stackTraceToString()); pending = null }
    }
}
