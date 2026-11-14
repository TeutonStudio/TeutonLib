package de.TeutonStudio.TeutonLib.tanktest.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import de.TeutonStudio.TeutonLib.client.fluid.SableTankFluidRenderer
import de.TeutonStudio.TeutonLib.integration.fluid.TankFluidGeometry
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.SubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.fluids.FluidStack
import org.joml.Quaterniond
import org.joml.Vector3d
import org.lwjgl.opengl.GL11
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Runs only in a launcher-created copy. All fixture changes are on the integrated server thread. */
@EventBusSubscriber(modid = "teutonlib_tank_fluid_test", value = [Dist.CLIENT])
object TankFluidClientVerification {
    private val enabled = java.lang.Boolean.getBoolean("teutonlib.tankFluidClientTests")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val log = LoggerFactory.getLogger("TeutonLib/TankFluidClientTest")
    private data class Stage(val name: String, val fraction: Double?, val rotation: Quaterniond?, val translated: Boolean = false)
    private data class Fixture(val ship: UUID, val controller: BlockPos, val expectedFraction: Double?, val inventory: JsonObject)
    private val stages = buildList {
        add(Stage("saved-chocolate", null, null))
        for ((name, rotation) in listOf(
            "upright" to Quaterniond(),
            "sideways" to Quaterniond().rotateY(.55).rotateZ(Math.PI / 2),
            "tilted" to Quaterniond().rotateY(.65).rotateZ(.62).rotateX(.31),
        )) for (fraction in listOf(.1, .5, .9)) add(Stage("$name-${(fraction * 100).toInt()}", fraction, rotation))
        add(Stage("tilted-90-cached", .9, Quaterniond().rotateY(.65).rotateZ(.62).rotateX(.31)))
        add(Stage("translated-90", .9, Quaterniond().rotateY(.65).rotateZ(.62).rotateX(.31), true))
    }
    private val captures = JsonArray()
    private var initialized = false
    private var finished = false
    private var index = 0
    private var wait = 0
    private var fixture: Fixture? = null
    private var work: CompletableFuture<Fixture>? = null
    private var pendingScreenshot = false
    private var age = 0
    private var original: JsonObject? = null
    private var lastBuildCount: Long? = null
    private var previousDrawCall = 0L

    private fun directory() = Minecraft.getInstance().gameDirectory.toPath().resolve("tank-fluid-verification")
    private fun entities(ship: SubLevel) = ship.plot.loadedChunks.flatMap { it.chunk.blockEntities.values.toList() }
    private fun controllers(ship: SubLevel) = entities(ship).filterIsInstance<FluidTankBlockEntity>().filter { it.isController }
    private fun point(value: Vec3) = gson.toJsonTree(listOf(value.x, value.y, value.z))

    @SubscribeEvent
    @JvmStatic
    fun tick(event: ClientTickEvent.Post) {
        if (!enabled || finished) return
        try {
            val mc = Minecraft.getInstance()
            if (!initialized) {
                check(Files.isRegularFile(mc.gameDirectory.toPath().resolve("tank-fluid-copy.marker"))) {
                    "Refusing to run outside the disposable tank-fluid launcher directory"
                }
                Files.createDirectories(directory())
                mc.window.setWindowed(1440, 900)
                mc.options.hideGui = true
                mc.options.pauseOnLostFocus = false
                mc.options.renderDistance().set(8)
                mc.options.framerateLimit().set(60)
                mc.options.enableVsync().set(false)
                initialized = true
            }
            if (++age > 20 * 240) error("Tank fluid client fixture timed out at stage $index")
            val server = mc.singleplayerServer ?: return
            if (mc.level == null || mc.player == null || mc.screen != null || mc.overlay != null) return
            if (pendingScreenshot) return
            work?.let { task ->
                if (!task.isDone) return
                fixture = task.join()
                work = null
                wait = 65 // Allow networking, sublevel interpolation, fluid-level chasing and chunk compilation.
                return
            }
            if (wait > 0) { wait--; return }
            if (fixture == null) {
                val stage = stages[index]
                work = CompletableFuture<Fixture>().also { future -> server.execute {
                    try { future.complete(prepare(server, stage)) }
                    catch (failure: Throwable) { future.completeExceptionally(failure) }
                } }
            }
        } catch (failure: Throwable) { fail(failure) }
    }

    private fun prepare(server: MinecraftServer, stage: Stage): Fixture {
        val world = server.overworld()
        val container = checkNotNull(SubLevelContainer.getContainer(world))
        val ships = container.allSubLevels.filterIsInstance<ServerSubLevel>()
        check(ships.size >= 2) { "Expected the two Tank Test sublevels, found ${ships.size}" }
        val subject = fixture?.ship?.let { id -> ships.firstOrNull { it.uniqueId == id } }
            ?: ships.firstOrNull { ship -> controllers(ship).any { !it.tankInventory.fluid.isEmpty } }
            ?: error("No filled sublevel tank found in copied Tank Test")
        val tank = controllers(subject).single()
        check(tank.width == 3 && tank.height == 9) { "Unexpected real fixture size ${tank.width} × ${tank.height}" }
        val physics = SubLevelPhysicsSystem.require(world)
        physics.setPaused(true)
        if (original == null) {
            original = JsonObject().also { inventory ->
                inventory.addProperty("levelName", server.worldData.levelName)
                inventory.add("sublevels", JsonArray().also { array -> ships.forEach { ship ->
                    array.add(JsonObject().also {
                        it.addProperty("ship", ship.uniqueId.toString())
                        it.add("pose", gson.toJsonTree(ship.logicalPose()))
                        it.add("blockEntities", JsonArray().also { list -> entities(ship).forEach { be ->
                            list.add(JsonObject().also { row ->
                                row.addProperty("class", be.javaClass.name)
                                row.add("plot", point(be.blockPos.center))
                                row.add("world", point(ship.logicalPose().transformPosition(be.blockPos.center)))
                                if (be is FluidTankBlockEntity && be.isController) {
                                    row.addProperty("width", be.width); row.addProperty("height", be.height)
                                    row.addProperty("amount", be.tankInventory.fluidAmount)
                                    row.addProperty("capacity", be.tankInventory.capacity)
                                    row.addProperty("fluid", BuiltInRegistries.FLUID.getKey(be.tankInventory.fluid.fluid).toString())
                                }
                            })
                        } })
                    })
                } })
            }
            Files.writeString(directory().resolve("original-world.json"), gson.toJson(original))
        }
        val targetCenter: Vec3
        if (stage.rotation != null) {
            // Keep the real tank multiblock; isolate external transfer only in the disposable copy.
            entities(subject).filterNot { it is FluidTankBlockEntity }.forEach { world.setBlock(it.blockPos, Blocks.AIR.defaultBlockState(), 3) }
            val target = Pose3d(subject.logicalPose())
            target.rotationPoint().set(tank.blockPos.x + tank.width / 2.0, tank.blockPos.y + tank.height / 2.0, tank.blockPos.z + tank.width / 2.0)
            targetCenter = if (stage.translated) Vec3(-13.0, 114.0, -18.0) else Vec3(-25.0, 110.0, -10.0)
            target.position().set(targetCenter.x, targetCenter.y, targetCenter.z)
            target.orientation().set(stage.rotation)
            subject.logicalPose().set(target)
            val handle = checkNotNull(RigidBodyHandle.of(subject))
            check(handle.isValid)
            handle.teleport(target.position(), target.orientation())
            physics.pipeline.resetVelocity(subject)
            subject.forceUpdateGlobalBounds()
            tank.tankInventory.setFluid(FluidStack(Fluids.WATER, (tank.tankInventory.capacity * checkNotNull(stage.fraction)).toInt()))
            tank.setChanged()
            tank.sendData()
        } else {
            targetCenter = subject.logicalPose().transformPosition(tank.blockPos.center.add(tank.width / 2.0 - .5, tank.height / 2.0 - .5, tank.width / 2.0 - .5))
        }
        val player = server.playerList.players.single()
        player.setGameMode(GameType.SPECTATOR)
        val camera = targetCenter.add(12.0, 3.0, 14.0)
        val delta = targetCenter.subtract(camera.add(0.0, 1.62, 0.0))
        val yaw = Math.toDegrees(atan2(delta.z, delta.x)).toFloat() - 90f
        val pitch = -Math.toDegrees(atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z))).toFloat()
        player.connection.teleport(camera.x, camera.y, camera.z, yaw, pitch)
        server.commands.performPrefixedCommand(server.createCommandSourceStack(), "time set 6000")
        server.commands.performPrefixedCommand(server.createCommandSourceStack(), "weather clear")
        return Fixture(subject.uniqueId, tank.blockPos.immutable(), stage.fraction, checkNotNull(original))
    }

    @SubscribeEvent
    @JvmStatic
    fun frame(event: RenderFrameEvent.Post) {
        if (!enabled || finished || pendingScreenshot || wait > 0 || work != null) return
        val selected = fixture ?: return
        try {
            val mc = Minecraft.getInstance()
            val world = mc.level ?: return
            if (mc.player == null || mc.screen != null || mc.overlay != null) return
            val ship = SubLevelContainer.getContainer(world)?.getSubLevel(selected.ship) as? ClientSubLevel ?: return
            val tank = world.getBlockEntity(selected.controller) as? FluidTankBlockEntity ?: return
            val draw = SableTankFluidRenderer.lastDraw(tank) ?: return
            val stage = stages[index]
            check(draw.renderCallCount > previousDrawCall) { "No new renderer-hook draw for ${stage.name}" }
            selected.expectedFraction?.let { check(abs(draw.fraction - it) < 2e-4) { "Fluid level has not settled: ${draw.fraction} != $it" } }
            check(draw.planeWorldYDeviation < 1e-5) { "Free surface is tilted in world space: ${draw.planeWorldYDeviation}" }
            val up = ship.renderPose().orientation().transform(Vector3d(draw.localUp.x, draw.localUp.y, draw.localUp.z))
            check(up.distance(Vector3d(0.0, 1.0, 0.0)) < 2e-5) { "Surface gravity differs from actual render pose: $up" }
            val volume = meshVolume(draw.mesh)
            val bounds = draw.bounds
            val capacity = (bounds.max.x - bounds.min.x) * (bounds.max.y - bounds.min.y) * (bounds.max.z - bounds.min.z)
            check(abs(volume / capacity - draw.fraction) < 2e-5) { "Visible volume does not preserve fill fraction: $volume / $capacity != ${draw.fraction}" }
            check(draw.mesh.faces.any { it.freeSurface }) { "Missing visible free surface" }
            if (stage.rotation != null) check(draw.meshReused) { "Static tank geometry was rebuilt every frame" }
            if (stage.name.endsWith("-cached") || stage.translated) check(draw.meshBuildCount == lastBuildCount) {
                "Unchanged local shape was rebuilt: ${draw.meshBuildCount} != $lastBuildCount"
            }
            val snapshot = JsonObject().also {
                it.addProperty("name", stage.name); it.addProperty("passed", true)
                it.addProperty("ship", selected.ship.toString())
                it.addProperty("renderer", GL11.glGetString(GL11.GL_RENDERER))
                it.addProperty("opengl", GL11.glGetString(GL11.GL_VERSION))
                it.addProperty("volume", volume); it.addProperty("capacityVolume", capacity)
                it.add("draw", gson.toJsonTree(draw)); it.add("worldSurfaceNormal", gson.toJsonTree(listOf(up.x, up.y, up.z)))
                it.add("camera", point(mc.player!!.position()))
                it.add("pose", gson.toJsonTree(ship.renderPose()))
            }
            lastBuildCount = draw.meshBuildCount
            previousDrawCall = draw.renderCallCount
            pendingScreenshot = true
            Screenshot.grab(mc.gameDirectory, "tank-fluid-${stage.name}.png", mc.mainRenderTarget,
                Consumer { _: Component? -> mc.execute {
                    try {
                        val screenshot = mc.gameDirectory.toPath().resolve("screenshots/tank-fluid-${stage.name}.png")
                        check(Files.isRegularFile(screenshot))
                        snapshot.addProperty("screenshot", screenshot.toAbsolutePath().toString())
                        Files.writeString(directory().resolve("${stage.name}.json"), gson.toJson(snapshot))
                        captures.add(snapshot)
                        pendingScreenshot = false
                        index++
                        log.info("Tank fluid captured {}", stage.name)
                        if (index == stages.size) {
                            Files.writeString(directory().resolve("report.json"), gson.toJson(JsonObject().also {
                                it.addProperty("passed", true); it.add("original", original); it.add("captures", captures)
                            }))
                            finished = true
                            mc.stop()
                        } else {
                            // Preserve subject identity while dispatching the next server stage.
                            val future = CompletableFuture<Fixture>()
                            work = future
                            val server = checkNotNull(mc.singleplayerServer)
                            server.execute {
                                try { future.complete(prepare(server, stages[index])) }
                                catch (failure: Throwable) { future.completeExceptionally(failure) }
                            }
                        }
                    } catch (failure: Throwable) { fail(failure) }
                } })
        } catch (failure: Throwable) { fail(failure) }
    }

    private fun meshVolume(mesh: TankFluidGeometry.Mesh): Double = abs(mesh.faces.sumOf { face ->
        if (face.vertices.size < 3) 0.0 else {
            val a = face.vertices.first()
            (1 until face.vertices.lastIndex).sumOf { index ->
                val b = face.vertices[index]; val c = face.vertices[index + 1]
                a.x * (b.y * c.z - b.z * c.y) + a.y * (b.z * c.x - b.x * c.z) + a.z * (b.x * c.y - b.y * c.x)
            }
        }
    } / 6.0)

    private fun fail(failure: Throwable) {
        log.error("Tank fluid client verification FAIL", failure)
        runCatching {
            Files.createDirectories(directory())
            Files.writeString(directory().resolve("report.json"), gson.toJson(JsonObject().also {
                it.addProperty("passed", false); it.addProperty("stage", stages.getOrNull(index)?.name)
                it.addProperty("failure", failure.stackTraceToString()); it.add("captures", captures)
            }))
        }
        finished = true
        Minecraft.getInstance().stop()
    }
}
