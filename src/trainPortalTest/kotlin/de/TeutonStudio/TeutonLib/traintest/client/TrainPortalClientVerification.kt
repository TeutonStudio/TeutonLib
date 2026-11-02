package de.TeutonStudio.TeutonLib.traintest.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.simibubi.create.CreateClient
import createtrainfunctionality.ClientLinks
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalClientLighting
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalCouplings
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalFlywheelLighting
import dev.engine_room.flywheel.api.backend.RenderContext
import dev.engine_room.flywheel.backend.engine.EngineImpl
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.Pose3dc
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.LightLayer
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import org.lwjgl.opengl.GL11
import org.slf4j.LoggerFactory
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleClientSessions
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleRender
import qouteall.imm_ptl.core.ClientWorldLoader
import qouteall.imm_ptl.core.compat.mixin_create.IPCarriageAccessor
import qouteall.imm_ptl.core.compat.create_integration.IPFlywheelStraddleClip
import qouteall.imm_ptl.core.render.context_management.PortalRendering
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import java.util.WeakHashMap

/** Opt-in screenshots and actual native draw counters in a disposable client directory. */
@EventBusSubscriber(modid = "teutonlib_train_portal_test", value = [Dist.CLIENT])
object TrainPortalClientVerification {
    private val enabled = java.lang.Boolean.getBoolean("teutonlib.trainPortalClientTests")
    private val log = LoggerFactory.getLogger("TeutonLib/TrainPortalClientTest")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private data class Capture(val name: String, var wait: Int = 40)
    private data class DrawKey(val ship: String, val ownerDimension: String, val renderingDimension: String,
        val projected: Boolean, val portalPass: Boolean, val clipping: Boolean)
    private val draws = linkedMapOf<DrawKey, Int>()
    private val lighting = linkedMapOf<DrawKey, JsonObject>()
    private val couplings = linkedMapOf<String, JsonObject>()
    private val appliedLighting = linkedMapOf<String, JsonObject>()
    private val flywheelLighting = linkedMapOf<String, JsonObject>()
    private val lastFlywheelDraw = WeakHashMap<ClientSubLevel, TrainPortalClientLighting.DrawLighting>()
    private val flywheelPasses = linkedMapOf<String, JsonObject>()
    private val captures = JsonArray()
    private val frameTimes = mutableListOf<Long>()
    private var pending: Capture? = null
    private var previousFrame = 0L
    private var initialized = false
    private var ready = false
    private var finishing = false
    private var finished = false
    private var orangeFrames = 0

    fun orangeFrame() { if (enabled && pending?.wait in 0..20) orangeFrames++ }

    private fun directory(): Path = Minecraft.getInstance().gameDirectory.toPath().resolve("train-portal-verification")

    @JvmStatic
    fun recordDraw(ship: ClientSubLevel, clipping: Boolean) {
        if (!enabled || pending?.wait !in 0..20) return
        val key = DrawKey(ship.uniqueId.toString(), ship.level.dimension().location().toString(),
            Minecraft.getInstance().level?.dimension()?.location().toString(), IPStraddleRender.isProjecting(),
            PortalRendering.isRendering(), clipping)
        draws[key] = (draws[key] ?: 0) + 1
        if (key !in lighting) lighting[key] = sampleLighting(ship, key)
    }

    /** Samples the illuminated side of the actual drawn build in its visible world. */
    private fun sampleLighting(ship: ClientSubLevel, key: DrawKey): JsonObject {
        val level = checkNotNull(Minecraft.getInstance().level)
        val box = ship.plot.boundingBox
        val pose = IPStraddleRender.overridePose(ship) ?: ship.renderPose()
        val plane = IPStraddleRender.planeFor(ship)
        return gson.toJsonTree(key).asJsonObject.also { sample ->
            sample.addProperty("sourceSkyScale", ship.latestSkyLightScale)
            sample.add("samples", JsonArray().also { points ->
                for (fraction in listOf(0.1, 0.5, 0.9)) {
                    val plot = Vec3(box.minX() + (box.maxX() - box.minX() + 1.0) * fraction,
                        box.maxY() + 1.5, (box.minZ() + box.maxZ() + 1.0) * 0.5)
                    val visible = pose.transformPosition(plot)
                    if (plane != null && plane.distance(visible) < -0.1) continue
                    val worldPosition = BlockPos.containing(visible)
                    val plotPosition = BlockPos.containing(plot)
                    points.add(JsonObject().also {
                        it.addProperty("x", visible.x); it.addProperty("y", visible.y); it.addProperty("z", visible.z)
                        it.addProperty("worldBlock", level.getBrightness(LightLayer.BLOCK, worldPosition))
                        it.addProperty("worldSky", level.getBrightness(LightLayer.SKY, worldPosition))
                        it.addProperty("plotBlock", ship.plot.lightEngine.getLayerListener(LightLayer.BLOCK).getLightValue(plotPosition))
                        it.addProperty("plotSky", ship.plot.lightEngine.getLayerListener(LightLayer.SKY).getLightValue(plotPosition))
                    })
                }
            })
        }
    }

    @JvmStatic
    fun recordCoupling(first: Vec3, second: Vec3, firstLight: Int, secondLight: Int,
        firstHead: Boolean, secondHead: Boolean) {
        if (!enabled || pending?.wait !in 0..20) return
        val dimension = Minecraft.getInstance().level?.dimension()?.location().toString()
        val portalPass = PortalRendering.isRendering()
        val key = "$dimension/$portalPass/$first/$second/$firstHead/$secondHead"
        val sample = couplings.getOrPut(key) { JsonObject().also {
            it.addProperty("dimension", dimension)
            it.addProperty("portalPass", portalPass)
            it.addProperty("length", first.distanceTo(second))
            it.addProperty("firstHead", firstHead); it.addProperty("secondHead", secondHead)
            it.addProperty("firstLight", firstLight); it.addProperty("secondLight", secondLight)
            it.add("first", gson.toJsonTree(listOf(first.x, first.y, first.z)))
            it.add("second", gson.toJsonTree(listOf(second.x, second.y, second.z)))
            it.addProperty("count", 0)
        } }
        sample.addProperty("count", sample.get("count").asInt + 1)
    }

    /** Called after native VBO draws, so this records uploaded UV2 rather than a synthetic query. */
    @JvmStatic
    fun recordAppliedLighting(ship: ClientSubLevel) {
        if (!enabled || pending?.wait !in 0..20) return
        val draw = TrainPortalClientLighting.lastDraw(ship) ?: return
        if (draw.level != Minecraft.getInstance().level?.dimension() || draw.projected != IPStraddleRender.isProjecting()) return
        val portalPass = PortalRendering.isRendering()
        val key = "${ship.uniqueId}/${draw.level}/${draw.projected}/$portalPass"
        val sample = appliedLighting.getOrPut(key) { JsonObject().also {
            it.addProperty("ship", ship.uniqueId.toString())
            it.addProperty("dimension", draw.level.location().toString())
            it.addProperty("projected", draw.projected); it.addProperty("portalPass", portalPass)
            it.addProperty("count", 0)
        } }
        sample.addProperty("count", sample.get("count").asInt + 1)
        sample.addProperty("vertices", draw.vertices)
        sample.addProperty("packedLight", draw.packedLight)
        sample.addProperty("originalPackedLight", draw.originalPackedLight)
        sample.addProperty("block", (draw.packedLight and 0xffff) shr 4)
        sample.addProperty("sky", (draw.packedLight ushr 16) shr 4)
        sample.addProperty("skyScale", TrainPortalClientLighting.skyLightScale(ship))
    }

    /** The far-half pass deliberately retains a source RenderContext; trust the draw's explicit light world. */
    @JvmStatic
    fun recordFlywheelLighting(context: RenderContext) {
        if (!enabled) return
        val ships = SubLevelContainer.getContainer(context.level())?.allSubLevels ?: return
        for (ship in ships.filterIsInstance<ClientSubLevel>()) {
            val draw = TrainPortalFlywheelLighting.lastDraw(ship) ?: continue
            // A draw object is created by the helper only when that ship's arena
            // participated in this pass. Ignore persistent diagnostics from older passes.
            if (lastFlywheelDraw.put(ship, draw) === draw) continue
            if (!enabled || pending?.wait !in 0..20) continue
            val portalPass = PortalRendering.isRendering()
            val key = "${ship.uniqueId}/${draw.level}/${draw.projected}/$portalPass"
            val sample = flywheelLighting.getOrPut(key) { JsonObject().also {
                it.addProperty("ship", ship.uniqueId.toString())
                it.addProperty("dimension", draw.level.location().toString())
                it.addProperty("projected", draw.projected); it.addProperty("portalPass", portalPass)
                it.addProperty("count", 0); it.addProperty("clearedSourceSkyInstances", 0)
            } }
            sample.addProperty("count", sample.get("count").asInt + 1)
            sample.addProperty("samples", draw.vertices)
            sample.addProperty("packedLight", draw.packedLight)
            sample.addProperty("originalPackedLight", draw.originalPackedLight)
            sample.addProperty("block", (draw.packedLight and 0xffff) shr 4)
            sample.addProperty("sky", (draw.packedLight ushr 16) shr 4)
            sample.addProperty("backend", TrainPortalFlywheelLighting.lastBackend())
            sample.addProperty("clearedSourceSkyInstances", maxOf(
                sample.get("clearedSourceSkyInstances").asInt, TrainPortalFlywheelLighting.lastInstanceSkyClears(ship)))
        }
    }

    @JvmStatic
    fun recordFlywheelPass(engine: EngineImpl, context: RenderContext, phase: String) {
        if (!enabled || pending?.wait !in 0..20) return
        val storage = engine.lightStorage()
        val farHalf = runCatching {
            IPFlywheelStraddleClip::class.java.getDeclaredField("farHalf").apply { isAccessible = true }.get(null)
        }.getOrNull()
        val key = "$phase/${context.level().dimension()}/${farHalf?.javaClass?.simpleName}"
        val sample = flywheelPasses.getOrPut(key) { JsonObject().also {
            it.addProperty("phase", phase); it.addProperty("contextDimension", context.level().dimension().location().toString())
            it.addProperty("count", 0)
        } }
        sample.addProperty("count", sample.get("count").asInt + 1)
        sample.addProperty("storage", storage.javaClass.name)
        sample.addProperty("backend", engine.drawManager().javaClass.simpleName)
        sample.addProperty("instancers", engine.drawManager().instancers().size)
        sample.addProperty("farHalfType", farHalf?.javaClass?.name)
        sample.addProperty("farHalf", farHalf?.toString())
        sample.add("arenaScenes", runCatching {
            val field = storage.javaClass.getDeclaredField("scene2SectionArenaIndexMap").apply { isAccessible = true }
            val scenes = field.get(storage) as Map<*, *>
            gson.toJsonTree(scenes.entries.associate { it.key.toString() to (it.value as? Map<*, *>)?.size })
        }.getOrElse { gson.toJsonTree(it.toString()) })
        sample.add("ships", JsonArray().also { array ->
            val container = SubLevelContainer.getContainer(context.level())
            sample.addProperty("containerDimension", container?.level?.dimension()?.location()?.toString())
            if (container != null) for (ship in container.allSubLevels.filterIsInstance<ClientSubLevel>()) {
                array.add(JsonObject().also {
                    it.addProperty("ship", ship.uniqueId.toString())
                    it.addProperty("scene", ship.lightingSceneId)
                    it.addProperty("sessions", IPStraddleClientSessions.renderSessionsOf(ship).size)
                    it.addProperty("removed", ship.isRemoved)
                    it.addProperty("lastDraw", TrainPortalFlywheelLighting.lastDraw(ship)?.toString())
                })
            }
        })
    }

    /** RCON harness sends tellraw markers after positioning the player and fixture. */
    @SubscribeEvent
    @JvmStatic
    fun message(event: ClientChatReceivedEvent.System) {
        if (!enabled || finished) return
        val message = event.message.string
        if (message.startsWith("TEUTONLIB_VISUAL_CAPTURE ")) {
            try {
                check(pending == null) { "Previous screenshot has not completed" }
                val name = message.removePrefix("TEUTONLIB_VISUAL_CAPTURE ")
                require(name.matches(Regex("[a-z0-9-]{1,64}"))) { "Invalid screenshot stage" }
                draws.clear()
                lighting.clear()
                couplings.clear()
                appliedLighting.clear()
                flywheelLighting.clear()
                flywheelPasses.clear()
                frameTimes.clear()
                orangeFrames = 0
                Minecraft.getInstance().entityRenderDispatcher.setRenderHitBoxes(
                    name == "orange-frame" || java.lang.Boolean.getBoolean("teutonlib.trainDemoHitboxes"))
                previousFrame = 0L
                pending = Capture(name)
                event.isCanceled = true
            } catch (failure: Throwable) { fail(failure) }
        } else if (message == "TEUTONLIB_VISUAL_FINISH") {
            finishing = true
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun tick(event: ClientTickEvent.Post) {
        TrainRiderClientDiagnostics.tick()
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
                pending?.let { if (it.wait > 0) it.wait-- }
            }
            if (finishing && pending == null) {
                check(captures.size() > 0) { "No screenshots captured" }
                Files.writeString(directory().resolve("report.json"), gson.toJson(JsonObject().also {
                    it.addProperty("passed", true)
                    it.add("captures", captures)
                }))
                log.info("Train portal client capture PASS: {} stages", captures.size())
                finished = true
                mc.stop()
            }
        } catch (failure: Throwable) { fail(failure) }
    }

    @SubscribeEvent
    @JvmStatic
    fun frame(event: RenderFrameEvent.Post) {
        if (!enabled || finished) return
        val capture = pending ?: return
        if (capture.wait !in 0..20) return
        val now = System.nanoTime()
        if (previousFrame != 0L) frameTimes += now - previousFrame
        previousFrame = now
        if (capture.wait != 0) return
        try {
            val mc = Minecraft.getInstance()
            if (mc.level == null || mc.player == null || mc.screen != null || mc.overlay != null) return
            val snapshot = snapshot(capture.name)
            capture.wait = -1
            Screenshot.grab(mc.gameDirectory, "train-portal-${capture.name}.png", mc.mainRenderTarget,
                Consumer { _: Component? ->
                    mc.execute {
                        try {
                            val screenshot = mc.gameDirectory.toPath().resolve("screenshots/train-portal-${capture.name}.png")
                            check(Files.isRegularFile(screenshot)) { "Screenshot was not written: $screenshot" }
                            snapshot.addProperty("screenshot", screenshot.toAbsolutePath().toString())
                            Files.writeString(directory().resolve("${capture.name}.json"), gson.toJson(snapshot))
                            captures.add(snapshot)
                            pending = null
                            log.info("Train portal client captured {}", capture.name)
                        } catch (failure: Throwable) { fail(failure) }
                    }
                })
        } catch (failure: Throwable) { fail(failure) }
    }

    private fun snapshot(name: String): JsonObject {
        val mc = Minecraft.getInstance()
        val worlds = (ClientWorldLoader.getClientWorlds() + listOfNotNull(mc.level)).distinctBy { it.dimension() }
        return JsonObject().also { result ->
            result.addProperty("name", name)
            result.addProperty("dimension", mc.level!!.dimension().location().toString())
            result.add("dimensionProperties", JsonObject().also { dimensions ->
                for (world in worlds) dimensions.add(world.dimension().location().toString(), JsonObject().also {
                    val type = world.dimensionType()
                    it.addProperty("hasSkyLight", type.hasSkyLight())
                    it.addProperty("minY", type.minY())
                    it.addProperty("height", type.height())
                    it.addProperty("logicalHeight", type.logicalHeight())
                    it.addProperty("ambientLight", type.ambientLight())
                    it.addProperty("hasFixedTime", type.hasFixedTime())
                    if (type.hasFixedTime()) it.addProperty("fixedTime", type.fixedTime().asLong)
                    it.addProperty("effectsLocation", type.effectsLocation().toString())
                })
            })
            result.addProperty("renderer", GL11.glGetString(GL11.GL_RENDERER))
            result.addProperty("opengl", GL11.glGetString(GL11.GL_VERSION))
            result.add("rider", JsonObject().also {
                val player = mc.player!!
                val support = (player as EntityMovementExtension).`sable$getTrackingSubLevel`()
                it.addProperty("support", support?.uniqueId?.toString())
                it.addProperty("supportRemoved", support?.isRemoved ?: false)
                it.addProperty("vehicle", player.vehicle?.uuid?.toString())
                it.addProperty("grounded", player.onGround())
            })
            result.add("camera", JsonObject().also {
                it.addProperty("x", mc.player!!.x); it.addProperty("y", mc.player!!.y); it.addProperty("z", mc.player!!.z)
                it.addProperty("yaw", mc.player!!.yRot); it.addProperty("pitch", mc.player!!.xRot)
            })
            result.add("sessions", JsonArray().also { array ->
                for (session in IPStraddleClientSessions.renderSessions()) {
                    val ship = IPStraddleRender.shipOf(session)
                    array.add(JsonObject().also {
                        it.addProperty("ship", session.ship().toString())
                        it.addProperty("portal", session.portal().toString())
                        it.addProperty("source", session.shipDim().location().toString())
                        it.addProperty("destination", session.destDim().location().toString())
                        it.addProperty("sourceReady", ship != null && !ship.isRemoved)
                        it.addProperty("clipPlane", ship != null && IPStraddleRender.planeFor(ship) != null)
                    })
                }
            })
            result.add("draws", JsonArray().also { array ->
                draws.forEach { (key, count) -> array.add(gson.toJsonTree(key).asJsonObject.also { it.addProperty("count", count) }) }
            })
            result.add("lighting", JsonArray().also { array -> lighting.values.forEach(array::add) })
            result.add("couplings", JsonArray().also { array -> couplings.values.forEach(array::add) })
            result.add("appliedLighting", JsonArray().also { array -> appliedLighting.values.forEach(array::add) })
            result.add("flywheelLighting", JsonArray().also { array -> flywheelLighting.values.forEach(array::add) })
            result.add("flywheelPasses", JsonArray().also { array -> flywheelPasses.values.forEach(array::add) })
            result.add("couplingDiagnostics", gson.toJsonTree(TrainPortalCouplings.debugState()))
            result.add("sublevels", JsonArray().also { array ->
                for (world in worlds) {
                    val ships = SubLevelContainer.getContainer(world)?.allSubLevels ?: continue
                    for (ship in ships.filterIsInstance<ClientSubLevel>()) array.add(JsonObject().also {
                        it.addProperty("ship", ship.uniqueId.toString())
                        it.addProperty("worldDimension", world.dimension().location().toString())
                        it.addProperty("shipDimension", ship.level.dimension().location().toString())
                        it.addProperty("removed", ship.isRemoved); it.addProperty("finalized", ship.isFinalized)
                        it.add("logicalPose", poseJson(ship.logicalPose()))
                        it.add("renderPose", poseJson(ship.renderPose()))
                        val bounds = ship.boundingBox()
                        it.add("globalBounds", gson.toJsonTree(listOf(bounds.minX(), bounds.minY(), bounds.minZ(),
                            bounds.maxX(), bounds.maxY(), bounds.maxZ())))
                        it.addProperty("plot", ship.plot.plotPos.toString())
                        it.addProperty("plotBounds", ship.plot.boundingBox.toString())
                        val data = ship.renderData
                        it.addProperty("renderData", data?.javaClass?.name)
                        it.addProperty("visibleSections", data?.visibleSectionCount)
                        if (data is de.TeutonStudio.TeutonLib.client.train.ContraptionLevel) {
                            val sections = data.graphicalSections().values
                            it.addProperty("graphicalSections", sections.size)
                            it.addProperty("modelBlocks", sections.sumOf { section -> section.models.size })
                            it.addProperty("animatedBlocks", sections.sumOf { section -> section.animated.size })
                            it.addProperty("capturedSections", data.capturedSections)
                        }
                    })
                }
            })
            result.add("boundCarriages", JsonArray().also { array ->
                for (train in CreateClient.RAILWAYS.trains.values) for (carriage in train.carriages) {
                    for ((dimension, record) in (carriage as IPCarriageAccessor).ip_getEntities()) {
                        val entity = record.entity.get()
                        array.add(JsonObject().also {
                            it.addProperty("train", train.id.toString()); it.addProperty("carriage", carriage.id)
                            it.addProperty("recordDimension", dimension.location().toString())
                            it.addProperty("entity", entity?.id)
                            it.addProperty("dimension", entity?.level()?.dimension()?.location()?.toString())
                            it.addProperty("removed", entity?.isRemoved)
                            it.addProperty("validForRender", entity?.validForRender)
                            it.addProperty("firstPositionUpdate", entity?.firstPositionUpdate)
                            it.addProperty("bound", entity?.carriage === carriage)
                            it.addProperty("plot", entity?.id?.let(ClientLinks::plotOriginOf)?.toShortString())
                            if (entity != null) {
                                it.add("position", gson.toJsonTree(listOf(entity.x, entity.y, entity.z)))
                                it.addProperty("yaw", entity.yRot); it.addProperty("pitch", entity.xRot)
                                it.addProperty("bounds", entity.boundingBox.toString())
                            }
                        })
                    }
                }
            })
            result.add("ctfClaims", JsonArray().also { array ->
                ClientLinks.entries().forEach { entry -> array.add(JsonObject().also {
                    it.addProperty("entity", entry.key); it.addProperty("plot", entry.value.toShortString())
                }) }
            })
            val sorted = frameTimes.sorted()
            result.addProperty("orangeFrames", orangeFrames)
            result.addProperty("hybridEnabled", de.TeutonStudio.TeutonLib.client.train.HybridTrainClient.enabled())
            net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.level!!.enabledFeatures(), mc.player!!.canUseGameMasterBlocks(), mc.level!!.registryAccess())
            val hiddenIds = listOf("functional_fob", "chunk_anchor_lever").map {
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create_train_functionality", it)
            }
            result.addProperty("hiddenItemsRegistered", hiddenIds.all { net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(it) })
            result.addProperty("hiddenItemsInCreative", net.minecraft.world.item.CreativeModeTabs.allTabs().any { tab ->
                (tab.displayItems + tab.searchTabDisplayItems).any { net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it.item) in hiddenIds }
            })
            result.add("frameTimes", JsonObject().also {
                it.addProperty("samples", sorted.size)
                if (sorted.isNotEmpty()) {
                    it.addProperty("medianMillis", sorted[sorted.size / 2] / 1_000_000.0)
                    it.addProperty("p95Millis", sorted[((sorted.size - 1) * 0.95).toInt()] / 1_000_000.0)
                }
            })
        }
    }

    private fun poseJson(pose: Pose3dc) = JsonObject().also {
        val position = pose.position(); val rotation = pose.orientation(); val pivot = pose.rotationPoint()
        it.add("position", gson.toJsonTree(listOf(position.x(), position.y(), position.z())))
        it.add("orientation", gson.toJsonTree(listOf(rotation.x(), rotation.y(), rotation.z(), rotation.w())))
        it.add("rotationPoint", gson.toJsonTree(listOf(pivot.x(), pivot.y(), pivot.z())))
    }

    private fun fail(failure: Throwable) {
        log.error("Train portal client capture FAIL", failure)
        runCatching {
            Files.createDirectories(directory())
            Files.writeString(directory().resolve("report.json"), gson.toJson(JsonObject().also {
                it.addProperty("passed", false); it.addProperty("failure", failure.stackTraceToString())
                it.add("captures", captures)
            }))
        }
        finished = true
        Minecraft.getInstance().stop()
    }
}
