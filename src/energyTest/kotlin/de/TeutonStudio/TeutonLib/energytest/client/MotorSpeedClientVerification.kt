package de.TeutonStudio.TeutonLib.energytest.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.InputConstants
import com.simibubi.create.CreateClient
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.minecraft.client.CameraType
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import org.slf4j.LoggerFactory
import java.nio.file.Files
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Disposable client fixture. Each new instruction.json stage is run once, using Minecraft's
 * normal use-key handling and Create's native settings screen and save packet.
 *
 * {"stage":"mounted","target":[plotX,y,plotZ],"side":"UP","desiredValue":40,"row":0}
 * targetWorld:[x,y,z] can instead identify the block by its projected centre. timeoutTicks
 * defaults to 200; finish:true writes report.json and stop:true also closes the client.
 */
@EventBusSubscriber(modid = "teutonlib_energy_test", value = [Dist.CLIENT])
object MotorSpeedClientVerification {
    private val enabled = java.lang.Boolean.getBoolean("teutonlib.motorSpeedClientTests")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val log = LoggerFactory.getLogger("TeutonLib/MotorSpeedClientTest")
    private val captures = JsonArray()
    private val completed = mutableSetOf<String>()
    private var initialized = false
    private var ready = false
    private var pending: Attempt? = null

    private class Attempt(val instruction: JsonObject, val stage: String) {
        var ticks = 0
        var useStarted = -1
        var savedAt = -1
        var openedAt = -1
        var target: BlockPos? = null
        var behaviour: ValueSettingsBehaviour? = null
        var side: Direction? = null
        var aim: Vec3? = null
        var nativeEvents = 0
        val observations = JsonArray()
        val report = JsonObject().also {
            it.addProperty("stage", stage)
            it.add("instruction", instruction.deepCopy())
        }
    }

    private fun directory() = Minecraft.getInstance().gameDirectory.toPath().resolve("motor-speed-verification")

    @SubscribeEvent(receiveCanceled = true)
    @JvmStatic
    fun interact(event: PlayerInteractEvent.RightClickBlock) {
        if (!enabled || !event.level.isClientSide) return
        val attempt = pending ?: return
        if (event.pos != attempt.target) return
        attempt.nativeEvents++
        if (attempt.nativeEvents <= 4) {
            attempt.observations.add(JsonObject().also {
                it.addProperty("kind", "native-right-click")
                it.addProperty("tick", attempt.ticks)
                it.addProperty("canceled", event.isCanceled)
                it.addProperty("result", event.cancellationResult.toString())
                it.add("hit", hit(event.hitVec))
            })
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun tick(event: ClientTickEvent.Post) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        try {
            if (!initialized) {
                Files.createDirectories(directory())
                mc.options.pauseOnLostFocus = false
                mc.options.cameraType = CameraType.FIRST_PERSON
                initialized = true
            }
            val player = mc.player ?: return
            val level = mc.level ?: return
            if (!ready && mc.overlay == null) {
                write("ready.json", JsonObject().also {
                    it.addProperty("player", player.gameProfile.name)
                    it.addProperty("dimension", level.dimension().location().toString())
                    it.add("position", vector(player.position()))
                })
                ready = true
            }
            if (pending == null) readInstruction()
            val attempt = pending ?: return
            attempt.ticks++
            if (attempt.ticks == 1) {
                mc.setScreen(null)
                mc.mouseHandler.grabMouse()
                mc.options.keyShift.isDown = false
                mc.options.keyAttack.isDown = false
                if (player.abilities.mayfly) {
                    player.abilities.flying = true
                    player.onUpdateAbilities()
                }
                for (slot in 0..8) {
                    if (BuiltInRegistries.ITEM.getKey(player.inventory.getItem(slot).item).toString() == "create:wrench") {
                        player.inventory.selected = slot
                        break
                    }
                }
                attempt.report.add("initial", snapshot(attempt))
            }

            val screen = mc.screen as? ValueSettingsScreen
            if (screen != null && attempt.openedAt == -1) {
                attempt.openedAt = attempt.ticks
                attempt.report.add("opened", snapshot(attempt))
            }
            if (screen != null && attempt.savedAt == -1 && attempt.ticks - attempt.openedAt >= 3) {
                val currentSettings = attempt.behaviour?.valueSettings
                val row = attempt.instruction.get("row")?.asInt ?: currentSettings?.row() ?: 0
                val value = attempt.instruction.get("desiredValue")?.asInt ?: currentSettings?.value() ?: 0
                val coordinate = screen.getCoordinateOfValue(row, value)
                val selection = screen.getClosestCoordinate(coordinate.x.toInt(), coordinate.y.toInt())
                attempt.report.add("selectedSettings", settings(selection))
                attempt.report.addProperty("releaseAccepted", screen.mouseReleased(coordinate.x.toDouble(), coordinate.y.toDouble(), 1))
                mc.options.keyUse.isDown = false
                attempt.savedAt = attempt.ticks
            }
            if (attempt.savedAt >= 0) {
                if (attempt.ticks - attempt.savedAt >= 30) finish(attempt, null)
                return
            }

            resolveTarget(attempt)
            aim(attempt)
            if (attempt.ticks == 5 || attempt.ticks == 20 || attempt.ticks == 60) {
                attempt.observations.add(snapshot(attempt))
            }
            if (attempt.useStarted == -1 && attempt.target != null && attempt.aim != null && attempt.ticks >= 10) {
                val blockHit = mc.hitResult as? BlockHitResult
                val hitMatches = blockHit != null && blockHit.blockPos == attempt.target &&
                    attempt.behaviour?.testHit(blockHit.location) == true
                // Attempt even on a mismatching hit so failures record the actual native path.
                if (hitMatches || attempt.ticks >= 30) {
                    attempt.report.add("beforeUse", snapshot(attempt))
                    mc.options.keyUse.isDown = true
                    KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(1))
                    attempt.useStarted = attempt.ticks
                }
            } else if (attempt.useStarted >= 0) {
                mc.options.keyUse.isDown = true
            }
            if (attempt.ticks >= (attempt.instruction.get("timeoutTicks")?.asInt ?: 200)) {
                finish(attempt, "Native ValueSettingsScreen did not open before the timeout")
            }
        } catch (failure: Throwable) {
            log.error("Motor speed client verification failed", failure)
            pending?.let { finish(it, failure.stackTraceToString()) }
                ?: write("report.json", JsonObject().also {
                    it.addProperty("passed", false)
                    it.addProperty("failure", failure.stackTraceToString())
                })
        }
    }

    private fun readInstruction() {
        val path = directory().resolve("instruction.json")
        if (!Files.isRegularFile(path)) return
        val instruction = runCatching { JsonParser.parseString(Files.readString(path)).asJsonObject }.getOrNull() ?: return
        val stage = instruction.get("stage")?.asString ?: return
        require(stage.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Invalid stage name" }
        if (stage in completed) return
        if (instruction.get("finish")?.asBoolean == true) {
            completed.add(stage)
            writeReport()
            if (instruction.get("stop")?.asBoolean == true) Minecraft.getInstance().stop()
            return
        }
        pending = Attempt(instruction, stage)
    }

    private fun resolveTarget(attempt: Attempt) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val position = attempt.instruction.getAsJsonArray("target")?.let { BlockPos(it[0].asInt, it[1].asInt, it[2].asInt) }
            ?: attempt.instruction.getAsJsonArray("targetWorld")?.let { coordinates ->
                val projected = Vec3(coordinates[0].asDouble, coordinates[1].asDouble, coordinates[2].asDouble)
                val candidates = mutableListOf(BlockPos.containing(projected))
                SubLevelContainer.getContainer(level)?.allSubLevels?.forEach {
                    candidates.add(BlockPos.containing(it.logicalPose().transformPositionInverse(projected)))
                }
                candidates.firstOrNull { level.getBlockEntity(it) is SmartBlockEntity }
            } ?: return
        val entity = level.getBlockEntity(position) as? SmartBlockEntity ?: return
        val behaviour = entity.allBehaviours.filterIsInstance<ValueSettingsBehaviour>()
            .firstOrNull { it.isActive && it.acceptsValueSettings() } ?: return
        attempt.target = position
        attempt.behaviour = behaviour
        val transform = behaviour.slotPositioning
        val requestedSide = attempt.instruction.get("side")?.asString?.let { Direction.valueOf(it.uppercase()) }
        if (transform is ValueBoxTransform.Sided) {
            val localEye = Sable.HELPER.getContainingClient(position)?.logicalPose()
                ?.transformPositionInverse(mc.player!!.eyePosition) ?: mc.player!!.eyePosition
            val preferred = Direction.values().sortedByDescending {
                localEye.subtract(position.center).dot(Vec3.atLowerCornerOf(it.normal))
            }
            attempt.side = (listOfNotNull(requestedSide) + preferred).firstOrNull {
                transform.fromSide(it).shouldRender(level, position, entity.blockState)
            }
            attempt.side?.let { transform.fromSide(it) }
        } else attempt.side = requestedSide
        val localOffset = transform.getLocalOffset(level, position, entity.blockState) ?: return
        val localAim = Vec3.atLowerCornerOf(position).add(localOffset)
        attempt.aim = Sable.HELPER.getContainingClient(position)?.logicalPose()?.transformPosition(localAim) ?: localAim
    }

    private fun aim(attempt: Attempt) {
        if (Minecraft.getInstance().screen != null) return
        val player = Minecraft.getInstance().player ?: return
        val target = attempt.aim ?: return
        val delta = target.subtract(player.eyePosition)
        player.yRot = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        player.xRot = Math.toDegrees(-atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z))).toFloat()
        player.yHeadRot = player.yRot
        player.yRotO = player.yRot
        player.xRotO = player.xRot
    }

    private fun snapshot(attempt: Attempt): JsonObject {
        val mc = Minecraft.getInstance()
        val player = mc.player
        val level = mc.level
        val position = attempt.target
        val blockHit = mc.hitResult as? BlockHitResult
        return JsonObject().also { result ->
            result.addProperty("tick", attempt.ticks)
            result.addProperty("screen", mc.screen?.javaClass?.name)
            result.addProperty("hitType", mc.hitResult?.type?.name)
            result.add("hit", hit(blockHit))
            result.addProperty("heldTicks", CreateClient.VALUE_SETTINGS_HANDLER.interactHeldTicks)
            result.addProperty("heldPos", CreateClient.VALUE_SETTINGS_HANDLER.interactHeldPos?.toShortString())
            result.addProperty("keyUse", mc.options.keyUse.isDown)
            result.addProperty("nativeEvents", attempt.nativeEvents)
            result.addProperty("side", attempt.side?.name)
            result.add("aimWorld", attempt.aim?.let(::vector))
            if (player != null) {
                result.add("eyeWorld", vector(player.eyePosition))
                result.add("player", vector(player.position()))
                result.addProperty("yaw", player.yRot)
                result.addProperty("pitch", player.xRot)
                result.addProperty("heldItem", BuiltInRegistries.ITEM.getKey(player.mainHandItem.item).toString())
                result.addProperty("shiftDown", player.isShiftKeyDown)
            }
            if (position != null && level != null) {
                result.add("target", block(position))
                result.addProperty("block", BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).block).toString())
                val subLevel = Sable.HELPER.getContainingClient(position)
                result.addProperty("ship", subLevel?.uniqueId?.toString())
                result.add("targetWorld", vector(subLevel?.logicalPose()?.transformPosition(position.center) ?: position.center))
                result.addProperty("hitMatchesTarget", blockHit?.blockPos == position)
                val entity = level.getBlockEntity(position) as? SmartBlockEntity
                result.add("behaviours", JsonArray().also { descriptions ->
                    entity?.allBehaviours?.filterIsInstance<ValueSettingsBehaviour>()?.forEach { behaviour ->
                        descriptions.add(JsonObject().also {
                            it.addProperty("class", behaviour.javaClass.name)
                            it.addProperty("active", behaviour.isActive)
                            it.addProperty("acceptsSettings", behaviour.acceptsValueSettings())
                            it.addProperty("requiresWrench", behaviour.onlyVisibleWithWrench())
                            it.addProperty("netId", behaviour.netId())
                            it.add("settings", settings(behaviour.valueSettings))
                            it.addProperty("testHit", blockHit?.let { hit -> behaviour.testHit(hit.location) })
                            if (player != null) it.addProperty("mayInteract", behaviour.mayInteract(player))
                        })
                    }
                })
            }
        }
    }

    private fun finish(attempt: Attempt, failure: String?) {
        Minecraft.getInstance().options.keyUse.isDown = false
        attempt.report.add("final", snapshot(attempt))
        attempt.report.addProperty("boardOpened", attempt.openedAt >= 0)
        attempt.report.addProperty("saveInvoked", attempt.savedAt >= 0)
        attempt.report.addProperty("nativeEvents", attempt.nativeEvents)
        attempt.report.addProperty("openedAtTick", attempt.openedAt)
        attempt.report.addProperty("ticks", attempt.ticks)
        attempt.report.add("observations", attempt.observations)
        if (failure != null) attempt.report.addProperty("failure", failure)
        val wantedValue = attempt.instruction.get("desiredValue")?.asInt
        val wantedRow = attempt.instruction.get("row")?.asInt ?: 0
        val selected = attempt.report.getAsJsonObject("selectedSettings")
        val selectionMatches = wantedValue == null ||
            (selected?.get("value")?.asInt == wantedValue && selected?.get("row")?.asInt == wantedRow)
        attempt.report.addProperty("passed", failure == null && attempt.openedAt >= 0 && attempt.savedAt >= 0 &&
            attempt.nativeEvents > 0 && selectionMatches)
        write("${attempt.stage}.json", attempt.report)
        captures.add(attempt.report)
        completed.add(attempt.stage)
        pending = null
        writeReport()
        log.info("Motor speed client stage {} PASS={}", attempt.stage, attempt.report.get("passed"))
    }

    private fun hit(hit: BlockHitResult?): JsonObject? = hit?.let {
        JsonObject().also { result ->
            result.add("block", block(it.blockPos))
            result.add("locationLocal", vector(it.location))
            result.addProperty("side", it.direction.name)
            val level = Minecraft.getInstance().level
            if (level != null) result.add("locationWorld", vector(Sable.HELPER.projectOutOfSubLevel(level, it.location)))
        }
    }

    private fun settings(value: ValueSettingsBehaviour.ValueSettings) = JsonObject().also {
        it.addProperty("row", value.row())
        it.addProperty("value", value.value())
    }

    private fun block(position: BlockPos) = JsonArray().also {
        it.add(position.x); it.add(position.y); it.add(position.z)
    }

    private fun vector(position: Vec3) = JsonArray().also {
        it.add(position.x); it.add(position.y); it.add(position.z)
    }

    private fun writeReport() = write("report.json", JsonObject().also {
        it.addProperty("passed", captures.size() > 0 && captures.all { stage -> stage.asJsonObject.get("passed").asBoolean })
        it.add("captures", captures)
    })

    private fun write(name: String, value: JsonObject) {
        Files.createDirectories(directory())
        Files.writeString(directory().resolve(name), gson.toJson(value))
    }
}
