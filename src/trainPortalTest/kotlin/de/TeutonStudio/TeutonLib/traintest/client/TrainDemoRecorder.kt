package de.TeutonStudio.TeutonLib.traintest.client

import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.atan2
import kotlin.math.sqrt
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.Sable

/** Records only the Minecraft framebuffer; no desktop contents are captured. */
@EventBusSubscriber(modid = "teutonlib_train_portal_test", value = [Dist.CLIENT])
object TrainDemoRecorder {
    private val enabled = java.lang.Boolean.getBoolean("teutonlib.trainDemo")
    private val continuousHitboxes = java.lang.Boolean.getBoolean("teutonlib.trainDemoHitboxes")
    private data class Frame(val pixels: ByteArray, val copies: Int)
    private data class Camera(val entity: ArmorStand, val start: Vec3, val end: Vec3, val look: Vec3, val began: Long, val duration: Double)
    private var camera: Camera? = null
    private var encoder: Process? = null
    private var worker: Thread? = null
    private var queue: ArrayBlockingQueue<Frame>? = null
    private var began = 0L
    private var frames = 0
    private var clip = ""
    private var failure: Throwable? = null
    private var capturedFrames = 0
    private var framesWithoutHitboxes = 0
    private fun directory() = Minecraft.getInstance().gameDirectory.toPath().resolve("demo-recording")

    @SubscribeEvent @JvmStatic fun message(event: ClientChatReceivedEvent.System) {
        if (!enabled || !event.message.string.startsWith("TEUTONLIB_DEMO ")) return
        event.isCanceled = true
        val args = event.message.string.removePrefix("TEUTONLIB_DEMO ").split(' ')
        val mc = Minecraft.getInstance()
        try {
            Files.createDirectories(directory())
            when (args[0]) {
                "camera" -> {
                    fun v(i: Int) = Vec3(args[i].toDouble(), args[i+1].toDouble(), args[i+2].toDouble())
                    val first = v(1)
                    val entity = ArmorStand(checkNotNull(mc.level), first.x, first.y, first.z)
                    camera = Camera(entity, first, v(4), v(7), System.nanoTime(), args[10].toDouble())
                    mc.setCameraEntity(entity)
                    updateCamera()
                }
                "record" -> {
                    check(encoder == null)
                    clip = args[1]; require(clip.matches(Regex("[a-z0-9-]+")))
                    val target = mc.mainRenderTarget
                    encoder = ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pixel_format", "rgba",
                        "-video_size", "${target.width}x${target.height}", "-framerate", "30", "-i", "pipe:0", "-an",
                        "-c:v", "libopenh264", "-b:v", "12M", "-pix_fmt", "yuv420p",
                        directory().resolve("$clip.mp4").toString()).redirectError(directory().resolve("$clip-encoder.log").toFile()).start()
                    val process = checkNotNull(encoder)
                    val input = ArrayBlockingQueue<Frame>(5); queue = input
                    failure = null; frames = 0; capturedFrames = 0; framesWithoutHitboxes = 0; began = System.nanoTime()
                    worker = Thread({
                        try {
                            process.outputStream.buffered(4 * 1024 * 1024).use { out ->
                                while (true) {
                                    val frame = input.take()
                                    if (frame.copies == 0) break
                                    repeat(frame.copies) { out.write(frame.pixels) }
                                }
                            }
                        } catch (e: Throwable) { failure = e }
                    }, "TeutonLib-demo-encoder").also { it.start() }
                }
                "stop" -> {
                    val process = checkNotNull(encoder)
                    check(queue!!.offer(Frame(ByteArray(0), 0), 3, java.util.concurrent.TimeUnit.SECONDS))
                    worker!!.join(10000)
                    check(failure == null) { failure.toString() }
                    check(process.waitFor() == 0)
                    encoder = null; worker = null; queue = null
                    Files.writeString(directory().resolve("$clip.json"), "{\"frames\":$frames,\"fps\":30,\"continuousHitboxes\":$continuousHitboxes,\"capturedFrames\":$capturedFrames,\"framesWithoutHitboxes\":$framesWithoutHitboxes}")
                }
                "snapshot" -> Screenshot.grab(mc.gameDirectory, "demo-${args[1]}.png", mc.mainRenderTarget) {}
                "inspect" -> {
                    val level = checkNotNull(mc.level)
                    val entities = level.entitiesForRendering().filterIsInstance<AbstractContraptionEntity>().map {
                        val sub = Sable.HELPER.getContaining(it)
                        val container = SubLevelContainer.getContainer(level)
                        val info = if (sub == null || container == null) null else dev.ryanhcode.sable.neoforge.compatibility.flywheel.FlywheelCompatNeoForge.getInfo(
                            net.minecraft.world.level.ChunkPos.asLong(sub.plot.plotPos.x - container.origin.x, sub.plot.plotPos.z - container.origin.y))
                        mapOf("id" to it.id, "uuid" to it.uuid.toString(), "pos" to it.position().toString(),
                            "sub" to sub?.uniqueId?.toString(), "embeddingSub" to info?.subLevelID?.toString(),
                            "bounds" to it.boundingBox.toString(), "blocks" to it.contraption?.blocks?.size,
                            "alive" to it.isAlive, "type" to it.javaClass.name)
                    }
                    val bearings = SubLevelContainer.getContainer(level)?.allSubLevels.orEmpty().flatMap { sub ->
                        sub.plot.loadedChunks.flatMap { it.chunk.blockEntities.values }.filterIsInstance<MechanicalBearingBlockEntity>().map {
                            mapOf("pos" to it.blockPos.toString(), "running" to it.isRunning, "entity" to it.movedContraption?.id,
                                "angle" to it.getInterpolatedAngle(1f), "class" to it.javaClass.name)
                        }
                    }
                    Files.writeString(directory().resolve("inspect-${args[1]}.json"), com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(mapOf("entities" to entities, "bearings" to bearings)))
                }
                "bounds" -> mc.entityRenderDispatcher.setRenderHitBoxes(args[1].toBoolean())
                "quit" -> mc.stop()
            }
        } catch (e: Throwable) {
            failure = e
            Files.writeString(directory().resolve("failure.txt"), e.stackTraceToString())
        }
    }

    private fun updateCamera() {
        val shot = camera ?: return
        val mc = Minecraft.getInstance()
        if (shot.entity.level() !== mc.level) return
        val t = ((System.nanoTime() - shot.began) / 1e9 / shot.duration).coerceIn(0.0, 1.0)
        val position = shot.start.lerp(shot.end, t)
        val delta = shot.look.subtract(position)
        val yaw = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        val pitch = -Math.toDegrees(atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z))).toFloat()
        shot.entity.setPos(position.x, position.y - shot.entity.eyeHeight, position.z)
        shot.entity.xo = shot.entity.x; shot.entity.yo = shot.entity.y; shot.entity.zo = shot.entity.z
        shot.entity.yRot = yaw; shot.entity.yRotO = yaw
        shot.entity.yHeadRot = yaw; shot.entity.yHeadRotO = yaw
        shot.entity.xRot = pitch; shot.entity.xRotO = pitch
        mc.setCameraEntity(shot.entity)
    }

    @SubscribeEvent @JvmStatic fun before(event: RenderFrameEvent.Pre) {
        if (!enabled) return
        if (continuousHitboxes) Minecraft.getInstance().entityRenderDispatcher.setRenderHitBoxes(true)
        updateCamera()
    }

    @SubscribeEvent @JvmStatic fun frame(event: RenderFrameEvent.Post) {
        if (!enabled || encoder == null || failure != null) return
        val wanted = (((System.nanoTime() - began) / 1e9) * 30).toInt() + 1
        if (wanted <= frames) return
        try {
            val hitboxes = Minecraft.getInstance().entityRenderDispatcher.shouldRenderHitBoxes()
            capturedFrames++
            if (!hitboxes) framesWithoutHitboxes++
            check(!continuousHitboxes || hitboxes) { "F3+B was disabled during the recording" }
            Screenshot.takeScreenshot(Minecraft.getInstance().mainRenderTarget).use { image ->
                val pixels = ByteBuffer.allocate(image.width * image.height * 4).order(ByteOrder.LITTLE_ENDIAN)
                pixels.asIntBuffer().put(image.pixelsRGBA)
                check(queue!!.offer(Frame(pixels.array(), (wanted - frames).coerceAtMost(30)), 3, java.util.concurrent.TimeUnit.SECONDS)) {
                    "Video encoder stopped consuming frames: $failure"
                }
                frames = wanted
            }
        } catch (e: Throwable) { failure = e; Files.writeString(directory().resolve("failure.txt"), e.stackTraceToString()) }
    }
}
