package de.TeutonStudio.TeutonLib.energytest

import com.mojang.authlib.GameProfile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour
import de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry
import de.TeutonStudio.TeutonLib.TeutonLib
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.util.FakePlayerFactory

/** Uses unmodified New Age at runtime, without adding it to the compile classpath. */
object MotorSpeedVerification {
    private val ORIGIN = BlockPos(88, 220, 24)
    private val FIXTURE_ORIGIN = BlockPos(8, 220, 8)
    private val PROFILE = GameProfile(UUID.fromString("6df6f7d6-1f62-4c87-bd71-67528dedbf10"), "MotorSpeedTest")
    private var fixtureShip: ServerSubLevel? = null
    private var fixturePos: BlockPos? = null
    private var fixturePhase = "clear"
    private var fixtureLevel: ServerLevel? = null
    private var fixturePose: Pose3d? = null
    private var fixtureLastPresentState: JsonObject? = null
    private var fixtureMissingReported = false

    fun run(level: ServerLevel): String {
        val player = FakePlayerFactory.get(level, PROFILE)
        val oldPosition = player.position()
        player.setGameMode(GameType.SURVIVAL)
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack())
        var assertions = 0
        // Sable retires plots asynchronously. Keep each ship alive until the suite
        // ends so the next variant cannot resolve a just-removed plot's entity.
        val ships = mutableListOf<ServerSubLevel>()
        try {
            for (variant in listOf("basic_motor", "advanced_motor", "reinforced_motor")) {
                clearBlocks(level, ORIGIN)
                place(level, ORIGIN, variant)
                assertions += verifyPackets(level, ORIGIN, player, variant, "world")
                val ship = assemble(level, ORIGIN)
                ships.add(ship)
                val pos = ship.plot.centerBlock
                assertions += verifyPackets(level, pos, player, variant, "assembled")
                move(level, ship, false)
                assertions += verifyPackets(level, pos, player, variant, "translated")
                move(level, ship, true)
                assertions += verifyPackets(level, pos, player, variant, "rotated")
            }
        } finally {
            val container = SubLevelContainer.getContainer(level)
            ships.forEach { container!!.removeSubLevel(it, SubLevelRemovalReason.REMOVED) }
            clearBlocks(level, ORIGIN)
            player.setPos(oldPosition)
            player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY)
        }
        return "Motor speed verification PASS: 3 variants, 4 poses, both signs, near/far packets, $assertions assertions"
    }

    private fun verifyPackets(level: ServerLevel, pos: BlockPos, player: ServerPlayer, variant: String, pose: String): Int {
        val phase = "$variant $pose"
        val expectedId = ResourceLocation.fromNamespaceAndPath("create_new_age", variant)
        val actualId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).block)
        check(actualId == expectedId) { "$phase must contain $expectedId at $pos, got $actualId" }
        val motor = level.getBlockEntity(pos) as KineticBlockEntity
        val behaviour = motor.javaClass.getField("speedBehavior").get(motor) as ScrollValueBehaviour
        val world = SubLevelGeometry.projectToWorld(level, pos.center)
        var assertions = 1
        // Assembly queues attachment for both kinetic block entities. Tick both ends
        // as a normal server would before checking the output, not just the motor.
        settleKinetics(level, motor)
        check(motor.theoreticalSpeed == behaviour.value.toFloat()) {
            "$phase existing speed must drive motor after initialization: ${diagnostics(level, pos)}"
        }
        check((level.getBlockEntity(pos.east()) as KineticBlockEntity).speed == behaviour.value.toFloat()) {
            "$phase existing speed must reach shaft after initialization: ${diagnostics(level, pos)}"
        }
        assertions += 2
        for (speed in listOf(32, -24)) {
            player.setPos(world.add(0.0, -player.eyeHeight.toDouble(), 2.0))
            val beforePacket = diagnostics(level, pos)
            packet(pos, speed, behaviour).handle(player)
            check(level.getBlockEntity(pos) === motor) {
                "$phase nearby packet $speed removed motor; before=$beforePacket after=${diagnostics(level, pos)}"
            }
            check(behaviour.value == speed) { "$phase nearby packet expected $speed, got ${behaviour.value}; plot=$pos world=$world before=$beforePacket after=${diagnostics(level, pos)}" }
            settleKinetics(level, motor)
            val generated = (motor.javaClass.getMethod("getGeneratedSpeed").invoke(motor) as Number).toFloat()
            check(generated == speed.toFloat()) { "$phase motor generated $generated instead of $speed" }
            val shaft = level.getBlockEntity(pos.east()) as KineticBlockEntity
            check(shaft.speed == speed.toFloat()) { "$phase shaft received ${shaft.speed} instead of $speed; before=$beforePacket after=${diagnostics(level, pos)}" }
            player.setPos(world.add(96.0, 0.0, 0.0))
            packet(pos, if (speed > 0) -16 else 16, behaviour).handle(player)
            check(behaviour.value == speed) { "$phase distant packet must be rejected" }
            assertions += 4
        }
        return assertions
    }

    private fun packet(pos: BlockPos, speed: Int, behaviour: ScrollValueBehaviour) =
        ValueSettingsPacket(pos, if (speed < 0) 0 else 1, kotlin.math.abs(speed), null, null, Direction.UP, false, behaviour.netId())

    private fun powerAndTick(level: ServerLevel, motor: KineticBlockEntity) {
        check(level.getBlockEntity(motor.blockPos) === motor) { "Motor removed before tick: ${diagnostics(level, motor.blockPos)}" }
        motor.javaClass.getField("needsPower").setBoolean(motor, false)
        motor.javaClass.getField("powered").setBoolean(motor, false)
        val energy = checkNotNull(level.getCapability(Capabilities.EnergyStorage.BLOCK, motor.blockPos, null)) {
            "Motor FE capability missing: ${diagnostics(level, motor.blockPos)}"
        }
        energy.receiveEnergy(Int.MAX_VALUE, false)
        check(energy.energyStored > 0) { "Motor fixture must have FE before its real tick" }
        motor.tick()
    }

    private fun settleKinetics(level: ServerLevel, motor: KineticBlockEntity) {
        val shaft = level.getBlockEntity(motor.blockPos.east()) as KineticBlockEntity
        repeat(3) { tick ->
            val before = diagnostics(level, motor.blockPos)
            powerAndTick(level, motor)
            check(level.getBlockEntity(motor.blockPos) === motor) {
                "Motor removed by kinetic settle tick $tick; before=$before after=${diagnostics(level, motor.blockPos)}"
            }
            shaft.tick()
            check(level.getBlockEntity(motor.blockPos) === motor) {
                "Motor removed by shaft settle tick $tick; before=$before after=${diagnostics(level, motor.blockPos)}"
            }
        }
    }

    private fun diagnostics(level: ServerLevel, pos: BlockPos): JsonObject = JsonObject().apply {
        add("motor", kineticState(level, pos))
        add("shaft", kineticState(level, pos.east()))
    }

    private fun place(level: ServerLevel, origin: BlockPos, variant: String) {
        level.getChunkAt(origin)
        val block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create_new_age", variant)).orElseThrow()
        level.setBlockAndUpdate(origin, block.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST))
        level.setBlockAndUpdate(origin.east(), AllBlocks.SHAFT.defaultState.setValue(BlockStateProperties.AXIS, Direction.Axis.X))
    }

    private fun assemble(level: ServerLevel, origin: BlockPos): ServerSubLevel =
        SubLevelAssemblyHelper.assembleBlocks(level, origin, listOf(origin, origin.east()), BoundingBox3i(origin, origin.east()))

    private fun move(level: ServerLevel, ship: ServerSubLevel, rotate: Boolean) {
        val target = Pose3d(ship.logicalPose())
        if (rotate) target.orientation().rotateY(Math.PI / 2).rotateZ(Math.PI / 6)
        else target.position().add(12.0, 7.0, -4.0)
        ship.logicalPose().set(target)
        RigidBodyHandle.of(ship)?.teleport(target.position(), target.orientation())
        SubLevelPhysicsSystem.require(level).pipeline.resetVelocity(ship)
    }

    /** Persistent fixture for the opt-in real-client board-opening verification. */
    fun fixture(level: ServerLevel, action: String): String {
        when (action) {
            "world" -> {
                clearFixture(level)
                for (x in -3..3) for (z in -3..3) level.setBlockAndUpdate(FIXTURE_ORIGIN.offset(x, -1, z), Blocks.STONE.defaultBlockState())
                place(level, FIXTURE_ORIGIN, "basic_motor")
                fixturePos = FIXTURE_ORIGIN
                fixtureLevel = level
                fixturePhase = "world"
                powerAndTick(level, level.getBlockEntity(FIXTURE_ORIGIN) as KineticBlockEntity)
            }
            "assemble" -> {
                check(fixturePhase == "world") { "Create world fixture before assembly" }
                fixtureShip = assemble(level, FIXTURE_ORIGIN)
                fixturePos = fixtureShip!!.plot.centerBlock
                fixturePose = Pose3d(fixtureShip!!.logicalPose())
                fixturePhase = "assembled"
            }
            "move" -> {
                val ship = checkNotNull(fixtureShip) { "Assemble fixture before movement" }
                move(level, ship, false)
                move(level, ship, true)
                fixturePose = Pose3d(ship.logicalPose())
                fixturePhase = "moved_rotated"
            }
            "clear" -> clearFixture(level)
            "status" -> Unit
            else -> error("Unknown speedFixture action $action")
        }
        val pos = fixturePos ?: return "Motor speed fixture {\"phase\":\"clear\"}"
        val world = SubLevelGeometry.projectToWorld(level, pos.center)
        val state = kineticState(level, pos)
        val motor = level.getBlockEntity(pos) as? KineticBlockEntity
        val behaviour = motor?.let { it.javaClass.getField("speedBehavior").get(it) as ScrollValueBehaviour }
        val report = JsonObject().apply {
            addProperty("phase", fixturePhase)
            addProperty("x", pos.x); addProperty("y", pos.y); addProperty("z", pos.z)
            addProperty("worldX", world.x); addProperty("worldY", world.y); addProperty("worldZ", world.z)
            addProperty("speed", behaviour?.value)
            addProperty("present", motor != null)
            add("motor", state)
            add("shaft", kineticState(level, pos.east()))
            fixtureLastPresentState?.let { add("lastPresentState", it.deepCopy()) }
        }
        return "Motor speed fixture $report"
    }

    private fun clearFixture(level: ServerLevel) {
        fixtureShip?.let { SubLevelContainer.getContainer(level)!!.removeSubLevel(it, SubLevelRemovalReason.REMOVED) }
        fixtureShip = null
        fixturePos = null
        fixtureLevel = null
        fixturePose = null
        fixtureLastPresentState = null
        fixtureMissingReported = false
        fixturePhase = "clear"
        clearBlocks(level, FIXTURE_ORIGIN)
        for (x in -3..3) for (z in -3..3) level.removeBlock(FIXTURE_ORIGIN.offset(x, -1, z), false)
    }

    /** Hold the disposable client fixture in place and keep its motor supplied. */
    fun tickFixture() {
        val level = fixtureLevel ?: return
        val pos = fixturePos ?: return
        fixtureShip?.let { ship ->
            val pose = fixturePose ?: return@let
            ship.logicalPose().set(pose)
            RigidBodyHandle.of(ship)?.teleport(pose.position(), pose.orientation())
            SubLevelPhysicsSystem.require(level).pipeline.resetVelocity(ship)
            ship.forceUpdateGlobalBounds()
        }
        level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null)?.receiveEnergy(Int.MAX_VALUE, false)
        if (level.getBlockEntity(pos) is KineticBlockEntity) {
            fixtureLastPresentState = JsonObject().apply {
                add("motor", kineticState(level, pos))
                add("shaft", kineticState(level, pos.east()))
            }
        } else if (!fixtureMissingReported) {
            fixtureMissingReported = true
            TeutonLib.LOGGER.error("Motor speed fixture lost motor at {}, last state={}", pos, fixtureLastPresentState)
        }
    }

    private fun kineticState(level: ServerLevel, pos: BlockPos): JsonObject = JsonObject().apply {
        addProperty("block", BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).block).toString())
        addProperty("gameTime", level.gameTime)
        val kinetic = level.getBlockEntity(pos) as? KineticBlockEntity ?: return@apply
        addProperty("speed", kinetic.speed)
        addProperty("kineticSpeed", kinetic.theoreticalSpeed)
        addProperty("generatedSpeed", kinetic.generatedSpeed)
        addProperty("network", kinetic.network)
        addProperty("updateSpeed", kinetic.updateSpeed)
        addProperty("overStressed", kinetic.isOverStressed)
        kinetic.source?.let { source -> add("source", JsonArray().apply { add(source.x); add(source.y); add(source.z) }) }
        for (fieldName in listOf("speed", "actualSpeed")) {
            runCatching { kinetic.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }.getFloat(kinetic) }
                .getOrNull()?.let { addProperty(if (fieldName == "speed") "newAgeLastSpeed" else "newAgeActualSpeed", it) }
        }
    }

    private fun clearBlocks(level: ServerLevel, origin: BlockPos) {
        level.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(origin.east(), Blocks.AIR.defaultBlockState())
    }
}
