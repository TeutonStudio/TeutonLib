package de.TeutonStudio.TeutonLib.energytest

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.TeutonLib
import java.lang.AssertionError
import java.lang.Exception
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.energy.EnergyStorage
import net.neoforged.neoforge.energy.IEnergyStorage
import net.neoforged.neoforge.event.RegisterCommandsEvent

/** Invoked only in disposable development worlds by tools/verify_server.py.  */
@Mod("teutonlib_energy_test")
class MotorEnergyVerification {
    init {
        NeoForge.EVENT_BUS.addListener<net.neoforged.neoforge.event.tick.ServerTickEvent.Post>(Consumer {
            MotorSpeedVerification.tickFixture()
        })
        NeoForge.EVENT_BUS.addListener<RegisterCommandsEvent>(Consumer { event: RegisterCommandsEvent? ->
            this.commands(
                event!!
            )
        })
    }

    private fun commands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("teutonlib_energy_test")
                .requires(Predicate { source: CommandSourceStack? -> source!!.hasPermission(4) })
                .executes(Command { ctx: CommandContext<CommandSourceStack> ->
                    try {
                        run(ctx!!.source!!.level)
                        ctx.source!!.sendSuccess(
                            Supplier { Component.literal("Motor energy verification PASS: 36 assemblies, 252 side queries, FE source, Flux Point, config, replacement") },
                            false
                        )
                        return@Command 1
                    } catch (error: Throwable) {
                        TeutonLib.Companion.LOGGER.error("Motor energy verification failed", error)
                        ctx!!.source!!.sendFailure(Component.literal("FAIL: " + error))
                        return@Command 0
                    }
                })
                .then(Commands.literal("prepare").executes(Command { ctx: CommandContext<CommandSourceStack> ->
                    val level = ctx!!.source!!.level
                    setup(level, "basic_motor_extension", "basic_motor", Direction.EAST)
                    cached = input(level, Direction.WEST)
                    ctx.source!!
                        .sendSuccess(Supplier { Component.literal("Cached motor energy view prepared") }, false)
                    1
                }))
                .then(
                    Commands.literal("lifecycle").then(
                        Commands.argument<String>("action", StringArgumentType.word())
                            .executes(Command { ctx: CommandContext<CommandSourceStack> ->
                                try {
                                    val action = StringArgumentType.getString(ctx, "action")
                                    val passed: Boolean = lifecycle(ctx!!.source!!.level, action)
                                    ctx.source!!.sendSuccess(
                                        Supplier { Component.literal("Motor lifecycle " + action + " PASS=" + passed) },
                                        false
                                    )
                                    return@Command if (passed) 1 else 0
                                } catch (error: Throwable) {
                                    TeutonLib.Companion.LOGGER.error("Motor lifecycle verification failed", error)
                                    ctx!!.source!!.sendFailure(Component.literal("FAIL: " + error))
                                    return@Command 0
                                }
                            })
                    )
                )
                .then(Commands.literal("probe").executes(Command { ctx: CommandContext<CommandSourceStack> ->
                    val enabled = ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.asBoolean
                    val passed = cached != null && cached!!.canReceive() == enabled && (cached!!.receiveEnergy(
                        1,
                        true
                    ) == 1) == enabled
                    ctx!!.source!!.sendSuccess(
                        Supplier { Component.literal("Cached energy view: enabled=" + enabled + " PASS=" + passed) },
                        false
                    )
                    if (passed) 1 else 0
                }))
                .then(Commands.literal("speed").executes { ctx ->
                    try {
                        val result = MotorSpeedVerification.run(ctx.source.level)
                        ctx.source.sendSuccess(Supplier { Component.literal(result) }, false)
                        1
                    } catch (error: Throwable) {
                        TeutonLib.LOGGER.error("Motor speed verification failed", error)
                        ctx.source.sendFailure(Component.literal("Motor speed verification FAIL: $error"))
                        0
                    }
                })
                .then(Commands.literal("speedFixture")
                    .then(Commands.argument("action", StringArgumentType.word()).executes { ctx ->
                        try {
                            val result = MotorSpeedVerification.fixture(ctx.source.level, StringArgumentType.getString(ctx, "action"))
                            ctx.source.sendSuccess(Supplier { Component.literal(result) }, false)
                            1
                        } catch (error: Throwable) {
                            TeutonLib.LOGGER.error("Motor speed fixture failed", error)
                            ctx.source.sendFailure(Component.literal("Motor speed fixture FAIL: $error"))
                            0
                        }
                    }))
        )
    }

    companion object {
        private val ORIGIN = BlockPos(8, 220, 8)
        private var cached: IEnergyStorage? = null
        private val LIFECYCLE_POS = BlockPos(16008, 220, 16008)
        private var lifecycleCached: IEnergyStorage? = null

        @Throws(Exception::class)
        @JvmStatic
        private fun lifecycle(level: ServerLevel, action: String): Boolean {
            if (action == "unloaded") {
                if (level.hasChunkAt(LIFECYCLE_POS)) return false
                require(
                    lifecycleCached != null && !lifecycleCached!!.canReceive() && lifecycleCached!!.receiveEnergy(
                        1,
                        false
                    ) == 0, "unloaded view rejects input"
                )
                require(!level.hasChunkAt(LIFECYCLE_POS), "energy query must not reload chunk")
                return true
            }
            level.getChunkAt(LIFECYCLE_POS)
            if (action == "prepare") {
                level.setBlockAndUpdate(LIFECYCLE_POS, Blocks.AIR.defaultBlockState())
                level.setBlockAndUpdate(LIFECYCLE_POS.east(), Blocks.AIR.defaultBlockState())
                level.setBlockAndUpdate(LIFECYCLE_POS, state("advanced_motor_extension", Direction.EAST))
                level.setBlockAndUpdate(LIFECYCLE_POS.east(), state("reinforced_motor", Direction.EAST))
                val motor = level.getBlockEntity(LIFECYCLE_POS.east())
                // Keep the motor switched off so its normal consumption cannot affect persistence checks.
                motor!!.javaClass.getField("needsPower").setBoolean(motor, true)
                lifecycleCached = level.getCapability<IEnergyStorage, Direction?>(
                    Capabilities.EnergyStorage.BLOCK,
                    LIFECYCLE_POS,
                    null
                )
                require(
                    lifecycleCached != null && lifecycleCached!!.receiveEnergy(23, false) == 23,
                    "prepare saved energy"
                )
                motor.setChanged()
                return true
            }
            val input =
                level.getCapability<IEnergyStorage, Direction?>(Capabilities.EnergyStorage.BLOCK, LIFECYCLE_POS, null)
            require(input != null && input.energyStored == 23, "preserved motor energy")
            require(input!!.receiveEnergy(1, false) == 1 && input.energyStored == 24, "restored connection")
            if (action == "reload") require(
                lifecycleCached != null && !lifecycleCached!!.canReceive(),
                "old chunk view stays invalid"
            )
            else require(action == "restart", "known lifecycle action")
            level.setBlockAndUpdate(LIFECYCLE_POS, Blocks.AIR.defaultBlockState())
            level.setBlockAndUpdate(LIFECYCLE_POS.east(), Blocks.AIR.defaultBlockState())
            return true
        }

        @Throws(Exception::class)
        @JvmStatic
        private fun run(level: ServerLevel) {
            val previous = ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.asBoolean
            ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(true)
            level.getChunkAt(ORIGIN)
            try {
                for (extension in arrayOf<String>("basic_motor_extension", "advanced_motor_extension")) {
                    for (motor in arrayOf<String>("basic_motor", "advanced_motor", "reinforced_motor")) {
                        for (direction in Direction.entries) {
                            setup(level, extension, motor, direction)
                            val motorPos: BlockPos = ORIGIN.relative(direction)
                            val storage = level.getCapability<IEnergyStorage, Direction?>(
                                Capabilities.EnergyStorage.BLOCK,
                                motorPos,
                                direction.opposite
                            )
                            require(storage != null, "motor FE capability")
                            for (side in Direction.entries) require(
                                input(level, side).canReceive(),
                                "input side " + side
                            )
                            val input: IEnergyStorage = input(level, null)
                            val before = storage!!.energyStored
                            require(input.receiveEnergy(41, true) == 41, "simulation accepts")
                            require(storage.energyStored == before, "simulation preserves energy")
                            val source = EnergyStorage(1000)
                            source.receiveEnergy(1000, false)
                            val accepted = input.receiveEnergy(source.extractEnergy(41, true), false)
                            source.extractEnergy(accepted, false)
                            require(
                                accepted == 41 && storage.energyStored == before + 41 && source.energyStored == 959,
                                "FE source accounting"
                            )
                            require(
                                input.energyStored == storage.energyStored
                                        && input.maxEnergyStored == storage.maxEnergyStored, "storage view"
                            )
                            require(!input.canExtract() && input.extractEnergy(41, false) == 0, "no extraction")
                            storage.receiveEnergy(Int.MAX_VALUE, false)
                            require(input.receiveEnergy(1, false) == 0, "full motor")
                            level.setBlockAndUpdate(motorPos, Blocks.AIR.defaultBlockState())
                            require(!input.canReceive() && input.receiveEnergy(1, false) == 0, "removed motor")
                            level.setBlockAndUpdate(motorPos, state(motor, direction.opposite))
                            require(!input.canReceive(), "wrong motor orientation")
                            level.setBlockAndUpdate(motorPos, state(motor, direction))
                            require(input.canReceive() && input.receiveEnergy(7, false) == 7, "motor reconnected")
                            ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(false)
                            require(!input.canReceive() && input.receiveEnergy(1, false) == 0, "cached config disable")
                            ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(true)
                            require(input.canReceive(), "cached config re-enable")
                            level.setBlockAndUpdate(ORIGIN, state(extension, direction.opposite))
                            require(!input.canReceive(), "rotated extension")
                            level.setBlockAndUpdate(ORIGIN, Blocks.AIR.defaultBlockState())
                            level.setBlockAndUpdate(ORIGIN, state(extension, direction))
                            require(!input.canReceive(), "old view cannot attach to replacement extension")
                        }
                    }
                }
                setup(level, "basic_motor_extension", "basic_motor", Direction.EAST)
                level.setBlockAndUpdate(ORIGIN.east(), state("basic_motor_extension", Direction.EAST))
                level.setBlockAndUpdate(ORIGIN.east(2), state("basic_motor", Direction.EAST))
                require(!input(level, Direction.WEST).canReceive(), "no extension chains")
                verifyFluxModels(level)
                verifyFluxPoint(level)
            } finally {
                ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(previous)
                clear(level)
            }
        }

        private fun verifyFluxModels(level: ServerLevel) {
            val connections = de.TeutonStudio.TeutonLib.integration.energy.FluxPointConnections
            val block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("fluxnetworks:flux_point"))
            val plug = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("fluxnetworks:flux_plug"))
            require(connections.REAR.values.all { block.defaultBlockState().hasProperty(it) && !block.defaultBlockState().getValue(it) }, "rear properties default false")
            require(connections.REAR.values.none { plug.defaultBlockState().hasProperty(it) }, "plug must not acquire point properties")
            val sideProperties = mapOf(Direction.DOWN to BlockStateProperties.DOWN, Direction.UP to BlockStateProperties.UP,
                Direction.NORTH to BlockStateProperties.NORTH, Direction.SOUTH to BlockStateProperties.SOUTH,
                Direction.WEST to BlockStateProperties.WEST, Direction.EAST to BlockStateProperties.EAST)
            for (extension in listOf("basic_motor_extension", "advanced_motor_extension")) for (side in Direction.entries) {
                setup(level, extension, "reinforced_motor", side)
                val pos = ORIGIN.relative(side.opposite)
                var state = block.defaultBlockState().setValue(sideProperties.getValue(side),true)
                level.setBlockAndUpdate(pos,state)
                connections.reconcile(level,pos)
                state = level.getBlockState(pos)
                require(state.getValue(connections.REAR.getValue(side)), "rear selected $extension/$side")
                val shape = state.getShape(level,pos).bounds()
                val minimum = when (side.axis) { Direction.Axis.X -> shape.minX; Direction.Axis.Y -> shape.minY; else -> shape.minZ }
                val maximum = when (side.axis) { Direction.Axis.X -> shape.maxX; Direction.Axis.Y -> shape.maxY; else -> shape.maxZ }
                require(if (side.axisDirection == Direction.AxisDirection.NEGATIVE) kotlin.math.abs(minimum+.125) < 1e-9 else kotlin.math.abs(maximum-1.125) < 1e-9, "rear reaches exact recessed face $side")
                ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(false)
                connections.reconcile(level,pos)
                require(!level.getBlockState(pos).getValue(connections.REAR.getValue(side)), "disabled integration clears rear")
                ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(true)
                level.setBlockAndUpdate(ORIGIN.relative(side), state("reinforced_motor",side.opposite))
                connections.reconcile(level,pos)
                require(!level.getBlockState(pos).getValue(connections.REAR.getValue(side)), "wrong motor clears rear")
                level.setBlockAndUpdate(ORIGIN.relative(side), state("reinforced_motor",side))
                for (input in Direction.entries.filter { it.axis != side.axis }) {
                    val sidePos = ORIGIN.relative(input.opposite)
                    level.setBlockAndUpdate(sidePos,block.defaultBlockState().setValue(sideProperties.getValue(input),true))
                    connections.reconcile(level,sidePos)
                    require(level.getBlockState(sidePos).getValue(connections.REAR.getValue(input)), "side input fits the recessed casing")
                    val fitted=connections.shape(level.getBlockState(sidePos),level,sidePos)
                    val collision=level.getBlockState(sidePos).getCollisionShape(level,sidePos)
                    require(fitted.bounds()==collision.bounds(), "collision shape follows extension orientation")
                }
            }
            clear(level)
            var all = block.defaultBlockState()
            for (side in Direction.entries) {
                val extensionPos = ORIGIN.relative(side)
                level.setBlockAndUpdate(extensionPos,state("basic_motor_extension",side))
                level.setBlockAndUpdate(ORIGIN.relative(side,2),state("basic_motor",side))
                all = all.setValue(sideProperties.getValue(side),true)
            }
            level.setBlockAndUpdate(ORIGIN,all)
            connections.reconcile(level,ORIGIN)
            require(connections.REAR.values.all { level.getBlockState(ORIGIN).getValue(it) }, "six simultaneous rear arms")
            setup(level,"basic_motor_extension","reinforced_motor",Direction.EAST)
            val point=ORIGIN.south()
            level.setBlockAndUpdate(point,block.defaultBlockState().setValue(BlockStateProperties.NORTH,true))
            connections.reconcile(level,point)
            val before=level.getBlockState(point)
            val sideShape=before.getCollisionShape(level,point).bounds()
            level.setBlockAndUpdate(ORIGIN.east(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
            level.setBlockAndUpdate(ORIGIN,state("basic_motor_extension",Direction.NORTH))
            level.setBlockAndUpdate(ORIGIN.north(),state("reinforced_motor",Direction.NORTH))
            connections.reconcile(level,point)
            require(level.getBlockState(point)===before,"rotation preserves the fitted connection state")
            require(before.getCollisionShape(level,point).bounds()!=sideShape,"rotation refreshes dynamic collision geometry")
            clear(level)
        }

        @Throws(Exception::class)
        @JvmStatic
        private fun verifyFluxPoint(level: ServerLevel) {
            setup(level, "advanced_motor_extension", "reinforced_motor", Direction.EAST)
            val pointBlock =
                BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("fluxnetworks:flux_point")).orElseThrow()
            level.setBlockAndUpdate(ORIGIN.west(), pointBlock.defaultBlockState())
            val point = level.getBlockEntity(ORIGIN.west())
            require(point != null, "Flux Point placed")
            // Run the actual Point's consumer discovery, simulation and delivery cycle. The
            // network-supplied buffer is seeded here so no player/network ownership is needed.
            val handler = point!!.javaClass.getMethod("getTransferHandler").invoke(point)
            val type: Class<*> = handler.javaClass
            type.getMethod(
                "updateSideTransfer",
                Direction::class.java,
                BlockEntity::class.java,
                Boolean::class.javaPrimitiveType
            )
                .invoke(handler, Direction.EAST, level.getBlockEntity(ORIGIN), false)
            type.getMethod("onCycleStart").invoke(handler)
            val request = type.getMethod("getRequest").invoke(handler) as Long
            require(request >= 37, "Flux Point requests energy")
            require(input(level, null).energyStored == 0, "Flux simulation preserves energy")
            type.getMethod("addToBuffer", Long::class.javaPrimitiveType).invoke(handler, 37L)
            type.getMethod("onCycleEnd").invoke(handler)
            require(input(level, null).energyStored == 37, "Flux Point delivers exactly 37 FE")
            ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(false)
            type.getMethod("addToBuffer", Long::class.javaPrimitiveType).invoke(handler, 13L)
            type.getMethod("onCycleStart").invoke(handler)
            type.getMethod("onCycleEnd").invoke(handler)
            ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(true)
            require(input(level, null).energyStored == 37, "disabled Flux delivery preserves motor")
            type.getMethod("onCycleStart").invoke(handler)
            type.getMethod("onCycleEnd").invoke(handler)
            require(input(level, null).energyStored == 50, "Flux Point resumes and retains undelivered energy")
        }

        @JvmStatic
        private fun setup(level: ServerLevel, extension: String, motor: String, direction: Direction) {
            clear(level)
            level.setBlockAndUpdate(ORIGIN, state(extension, direction))
            level.setBlockAndUpdate(ORIGIN.relative(direction), state(motor, direction))
        }

        @JvmStatic
        private fun clear(level: ServerLevel) {
            level.setBlockAndUpdate(ORIGIN, Blocks.AIR.defaultBlockState())
            for (direction in Direction.entries) {
                level.setBlockAndUpdate(ORIGIN.relative(direction), Blocks.AIR.defaultBlockState())
                level.setBlockAndUpdate(ORIGIN.relative(direction, 2), Blocks.AIR.defaultBlockState())
            }
        }

        @JvmStatic
        private fun state(name: String, facing: Direction): BlockState {
            val block =
                BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create_new_age", name))
                    .orElseThrow()
            return block.defaultBlockState().setValue<Direction, Direction>(BlockStateProperties.FACING, facing)
        }

        @JvmStatic
        private fun input(level: ServerLevel, side: Direction?): IEnergyStorage {
            val input: IEnergyStorage = level.getCapability<IEnergyStorage, Direction?>(
                Capabilities.EnergyStorage.BLOCK,
                MotorEnergyVerification.Companion.ORIGIN,
                side
            )!!
            require(input != null, "extension capability on " + side)
            return input
        }

        @JvmStatic
        private fun require(condition: Boolean, message: String?) {
            if (!condition) throw AssertionError(message)
        }
    }
}
