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
                verifyFluxPoint(level)
            } finally {
                ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(previous)
                clear(level)
            }
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
