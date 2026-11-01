package de.TeutonStudio.TeutonLib.traintest

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalRiders
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.Pose3dc
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.SubLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.LoggerFactory
import qouteall.imm_ptl.core.portal.Portal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Bounded observations only; never moves a player or changes a success assertion. */
@EventBusSubscriber(modid = "teutonlib_train_portal_test")
object TrainRiderDiagnostics {
    val enabled = java.lang.Boolean.getBoolean("teutonlib.trainPortalRiderDiagnostics")
    private val gson = Gson()
    private val counts = mutableMapOf<Path, Int>()
    private var warned = false
    private val log = LoggerFactory.getLogger("TeutonLib/TrainRiderDiagnostics")

    @SubscribeEvent @JvmStatic fun serverTick(event: ServerTickEvent.Post) {
        if (!enabled) return
        observe {
            val server = event.server
            val fixture = fixture(server) ?: return@observe
            val row = base("server-tick").apply {
                addProperty("serverTick", server.tickCount)
                add("fixture", fixture)
                add("players", JsonArray().apply { server.playerList.players.forEach { add(entity(it)) } })
                add("builds", JsonArray().apply {
                    for (link in SubLevelLinks.links()) {
                        val level = server.getLevel(link.dimension) ?: continue
                        val sub = SubLevelContainer.getContainer(level)?.getSubLevel(link.subLevelId) ?: continue
                        add(ship(sub))
                    }
                })
            }
            write(server.getFile("train-portal-verification/riders-server.jsonl"), row)
        }
    }

    @JvmStatic fun capture(captured: TrainPortalRiders, sub: ServerSubLevel, portal: Portal) {
        if (!enabled) return
        observe { recordTransfer("capture", captured, sub, portal, false) }
    }

    @JvmStatic fun restore(stage: String, captured: TrainPortalRiders, sub: ServerSubLevel, rollback: Boolean) {
        if (!enabled) return
        observe { recordTransfer(stage, captured, sub, null, rollback) }
    }

    private fun recordTransfer(stage: String, captured: TrainPortalRiders, sub: ServerSubLevel,
        portal: Portal?, rollback: Boolean) {
        val server = sub.level.server
        val source = field(captured, "source") as net.minecraft.server.level.ServerLevel
        val capturePose = field(captured, "pose") as Pose3dc
        val origin = field(captured, "origin") as BlockPos
        val shift = sub.plot.centerBlock.subtract(origin)
        val row = base(stage).apply {
            addProperty("serverTick", server.tickCount)
            addProperty("rollback", rollback)
            addProperty("source", source.dimension().location().toString())
            add("capturePose", pose(capturePose))
            add("currentShip", ship(sub))
            add("fixture", fixture(server))
            portal?.let { add("portal", JsonObject().apply {
                addProperty("id", it.uuid.toString())
                add("origin", point(it.originPos)); add("normal", point(it.normal))
                addProperty("destination", it.destDim.location().toString())
            }) }
            add("riders", JsonArray().apply {
                for (rider in field(captured, "riders") as List<*>) {
                    rider ?: continue
                    val id = field(rider, "id") as UUID
                    val plot = field(rider, "plotPosition") as Vec3
                    val current = server.playerList.getPlayer(id) ?: server.allLevels.firstNotNullOfOrNull { it.getEntity(id) }
                    val expected = sub.logicalPose().transformPosition(plot.add(shift.x.toDouble(), shift.y.toDouble(), shift.z.toDouble()))
                    add(JsonObject().apply {
                        addProperty("id", id.toString())
                        addProperty("sourceSide", field(rider, "sourceSide") as Boolean)
                        addProperty("latePlayer", field(rider, "latePlayer") as Boolean)
                        add("capturedPlot", point(plot))
                        add("capturedWorld", point(capturePose.transformPosition(plot)))
                        add("capturedVelocity", point(field(rider, "velocity") as Vec3))
                        add("expectedRestoredWorld", point(expected))
                        current?.let {
                            add("entity", entity(it))
                            addProperty("distanceToRestoredWorld", it.position().distanceTo(expected))
                        }
                    })
                }
            })
        }
        write(server.getFile("train-portal-verification/riders-server.jsonl"), row)
    }

    /** The fixture's original offset and phase expose when a persistent error first appears. */
    private fun fixture(server: MinecraftServer): JsonObject? {
        val run = TrainTraversalVerification::class.java.getDeclaredField("run").apply { isAccessible = true }.get(null) ?: return null
        val ride = field(run, "rider") ?: return null
        val id = field(run, "subId") as UUID
        return JsonObject().apply {
            addProperty("phase", field(ride, "phase") as Int)
            addProperty("wait", field(ride, "wait") as Int)
            addProperty("remainingTravel", field(run, "remainingTravel") as Double)
            addProperty("subId", id.toString())
            addProperty("seated", field(ride, "seated") as Boolean)
            addProperty("dimensionChanges", field(ride, "dimensionChanges") as Int)
            val offset = field(ride, "offset") as? Vec3
            if (offset != null) {
                add("initialOffset", point(offset))
                val sub = server.allLevels.firstNotNullOfOrNull { SubLevelContainer.getContainer(it)?.getSubLevel(id) }
                if (sub != null) add("expectedInBodyWorld", point(sub.logicalPose().transformPosition(
                    Vec3.atLowerCornerOf(sub.plot.centerBlock).add(offset))))
            }
        }
    }

    fun entity(entity: Entity): JsonObject = JsonObject().apply {
        addProperty("id", entity.uuid.toString()); addProperty("world", entity.level().dimension().location().toString())
        addProperty("tick", entity.tickCount); add("position", point(entity.position())); add("delta", point(entity.deltaMovement))
        addProperty("onGround", entity.onGround()); addProperty("vehicle", entity.vehicle?.uuid?.toString())
        addProperty("fallDistance", entity.fallDistance)
        val movement = entity as EntityMovementExtension
        val tracking = movement.`sable$getTrackingSubLevel`()
        if (tracking != null) add("tracking", ship(tracking))
        addProperty("lastTracking", movement.`sable$getLastTrackingSubLevelID`()?.toString())
        (entity as EntityStickExtension).`sable$getPlotPosition`()?.let { add("plotPosition", point(it)) }
        if (entity is LivingEntity) {
            val inherited = (entity as LivingEntityMovementExtension).`sable$getInheritedVelocity`()
            add("inheritedVelocity", point(Vec3(inherited.x, inherited.y, inherited.z)))
        }
        movement.`sable$getCollisionInfo`()?.let { info -> add("collision", JsonObject().apply {
            addProperty("below", info.verticalCollisionBelow)
            addProperty("tracking", info.trackingSubLevel?.uniqueId?.toString())
            addProperty("preTracking", info.preTrackingSubLevel?.uniqueId?.toString())
            info.inheritedMotion?.let { add("inheritedMotion", point(it)) }
            info.motion?.let { add("motion", point(it)) }
        }) }
    }

    fun ship(sub: SubLevel): JsonObject = JsonObject().apply {
        addProperty("id", sub.uniqueId.toString()); addProperty("world", sub.level.dimension().location().toString())
        addProperty("removed", sub.isRemoved); add("logicalPose", pose(sub.logicalPose())); add("lastPose", pose(sub.lastPose()))
    }

    fun pose(value: Pose3dc): JsonObject = JsonObject().apply {
        val p = value.position(); val r = value.rotationPoint(); val q = value.orientation()
        add("position", point(Vec3(p.x(), p.y(), p.z())))
        add("rotationPoint", point(Vec3(r.x(), r.y(), r.z())))
        add("orientation", JsonArray().apply { add(q.x()); add(q.y()); add(q.z()); add(q.w()) })
    }

    fun point(value: Vec3): JsonArray = JsonArray().apply { add(value.x); add(value.y); add(value.z) }
    fun base(stage: String): JsonObject = JsonObject().apply {
        addProperty("stage", stage); addProperty("epochMillis", System.currentTimeMillis())
    }

    fun write(path: Path, row: JsonObject) {
        val normalized = path.toAbsolutePath().normalize()
        val count = counts[normalized] ?: 0
        if (count >= 6000) return
        Files.createDirectories(normalized.parent)
        Files.writeString(normalized, gson.toJson(row) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        counts[normalized] = count + 1
    }

    fun observe(action: () -> Unit) {
        try { action() } catch (failure: Throwable) {
            if (!warned) { warned = true; log.error("Rider diagnostic recording failed", failure) }
        }
    }

    private fun field(instance: Any, name: String): Any? =
        instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)
}
