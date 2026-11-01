package de.TeutonStudio.TeutonLib.fluidtest

import com.drmangotea.tfmg.content.decoration.pipes.TFMGPipes
import com.drmangotea.tfmg.registry.TFMGBlocks
import com.simibubi.create.AllBlocks
import com.simibubi.create.content.fluids.FluidPropagator
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock
import com.simibubi.create.content.fluids.pump.PumpBlock
import com.simibubi.create.content.fluids.pump.PumpBlockEntity
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import com.yision.fluidlogistics.config.Config
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidPumpNetworkUpdater
import de.TeutonStudio.TeutonLib.integration.FluidPropagationCompatibility
import net.createmod.catnip.data.Couple
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import org.apache.commons.lang3.mutable.MutableBoolean

/** Disposable-world fixture; forces the lazily loaded target to transform even without a player. */
@Mod("teutonlib_fluid_test")
class FluidPropagationVerification {
    init {
        check(FluidPropagator::class.java.name.endsWith("FluidPropagator"))
        NeoForge.EVENT_BUS.register(this)
    }

    private val origin = BlockPos(0, 200, 0)
    private val lengths = listOf(4, 4, 22)
    private fun start(lane: Int) = origin.offset(0, 0, 32 + lane * 8)
    private fun pumpPos(lane: Int) = start(lane).east(lengths[lane])
    private fun output(lane: Int) = start(lane).east(lengths[lane] * 2)

    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        val root = Commands.literal("teutonlib_fluid_test").requires { it.hasPermission(4) }
        for (action in listOf("verify", "prepare", "sample", "break", "restore", "transform", "status")) {
            root.then(Commands.literal(action).executes { ctx ->
                try {
                    val result = when (action) {
                        "verify" -> verify(ctx.source.level)
                        "prepare" -> prepare(ctx.source.level)
                        "sample" -> sample(ctx.source.level)
                        "break" -> disconnect(ctx.source.level)
                        "restore" -> restore(ctx.source.level)
                        "status" -> status(ctx.source.level)
                        else -> "adapter=${FluidPropagationCompatibility.active()}"
                    }
                    ctx.source.sendSuccess({ Component.literal("Fluid $action PASS $result") }, false)
                    1
                } catch (failure: Throwable) {
                    org.slf4j.LoggerFactory.getLogger("TeutonLib/FluidTest").error("Fluid $action FAIL", failure)
                    ctx.source.sendFailure(Component.literal("Fluid $action FAIL $failure"))
                    0
                }
            })
        }
        event.dispatcher.register(root)
    }

    private fun force(level: ServerLevel) {
        for (x in -1..4) for (z in -1..4) level.setChunkForced(x, z, true)
    }

    private fun clearLine(level: ServerLevel, from: BlockPos, length: Int) {
        for (x in -1..length + 1) for (z in -2..2)
            level.setBlock(from.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3)
    }

    private fun pipeState(): BlockState = AllBlocks.FLUID_PIPE.get().getAxisState(Direction.Axis.X)
    private fun pipe(level: ServerLevel, pos: BlockPos) { level.setBlock(pos, pipeState(), 3) }
    private fun putPump(level: ServerLevel, pos: BlockPos, block: Block): PumpBlockEntity {
        level.setBlock(pos, block.defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST), 3)
        return (level.getBlockEntity(pos) as PumpBlockEntity).also { it.initialize() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun flags(pump: PumpBlockEntity): Couple<MutableBoolean> = PumpBlockEntity::class.java
        .getDeclaredField("sidesToUpdate").also { it.isAccessible = true }.get(pump) as Couple<MutableBoolean>

    private fun reset(pump: PumpBlockEntity) { flags(pump).forEach { it.setFalse() } }
    private fun notified(pump: PumpBlockEntity) = flags(pump).any { it.isTrue }

    private fun verify(level: ServerLevel): String {
        check(FluidPropagationCompatibility.active())
        check(Config.getFluidPumpRange() > FluidPropagator.getPumpRange())
        force(level)
        for (lane in lengths.indices) clearLine(level, start(lane), lengths[lane] * 2)
        // TFMG's overwrite must still recognize its mechanical materials and electric pump.
        val blocks = listOf(AllBlocks.MECHANICAL_PUMP.get()) + TFMGPipes.PIPES.values.map { it.pump.get() } +
            listOf(TFMGBlocks.ELECTRIC_PUMP.get())
        for (block in blocks) {
            clearLine(level, origin, 30)
            for (x in 0..3) pipe(level, origin.east(x))
            val pump = putPump(level, origin.east(4), block)
            reset(pump)
            FluidPropagator.propagateChangedPipe(level, origin, level.getBlockState(origin))
            check(notified(pump)) { "TFMG pump detection failed: $block" }
            level.removeBlock(pump.blockPos, false)
        }
        clearLine(level, origin, 30)
        val far = FluidPropagator.getPumpRange() + 5
        check(far < Config.getFluidPumpRange())
        for (x in 0 until far) pipe(level, origin.east(x))
        val pump = putPump(level, origin.east(far), com.yision.fluidlogistics.registry.AllBlocks.FLUID_PUMP.get())
        reset(pump)
        FluidPropagator.propagateChangedPipe(level, origin, level.getBlockState(origin))
        check(notified(pump)) { "extended-range pump was not notified beyond Create's cutoff" }
        // Existing pressure must be captured before Create wipes it, then fresh updates must work again.
        reset(pump)
        for (x in 0 until far) FluidPropagator.getPipe(level, origin.east(x)).addPressure(Direction.EAST, false, 32f)
        FluidPropagator.propagateChangedPipe(level, origin, level.getBlockState(origin))
        check(notified(pump)) { "pump notification failed with pre-existing pressure" }
        reset(pump)
        for (x in 0 until far) FluidPropagator.getPipe(level, origin.east(x)).wipePressure()
        Config.FLUID_PUMP_ENABLED.set(false)
        // Config has a cached runtime value; exercise its ordinary load handler through the test's field only.
        val enabled = Config::class.java.getDeclaredField("fluidPumpEnabled").also { it.isAccessible = true }
        enabled.setBoolean(null, false)
        try {
            FluidPropagator.propagateChangedPipe(level, origin, level.getBlockState(origin))
            check(!notified(pump)) { "disabled fluid pump still received an extended-range update" }
        } finally {
            enabled.setBoolean(null, true)
            Config.FLUID_PUMP_ENABLED.set(true)
        }
        level.removeBlock(pump.blockPos, false)
        clearLine(level, origin, 30)
        check(FluidPumpNetworkUpdater.createContext(level) == null) { "removed pump left stale loaded-pump bookkeeping" }
        return "${blocks.size} native pump types; extended range; pressure capture; disabled/no-pump paths"
    }

    private fun pumpBlock(lane: Int): Block = when (lane) {
        0 -> AllBlocks.MECHANICAL_PUMP.get()
        1 -> TFMGPipes.PIPES.values.first().pump.get()
        else -> com.yision.fluidlogistics.registry.AllBlocks.FLUID_PUMP.get()
    }

    private fun prepare(level: ServerLevel): String {
        force(level)
        for (lane in lengths.indices) {
            val from = start(lane)
            clearLine(level, from, lengths[lane] * 2)
            for (x in 1 until lengths[lane] * 2) pipe(level, from.east(x))
            putPump(level, pumpPos(lane), pumpBlock(lane))
            for (pos in listOf(from, output(lane))) {
                level.setBlock(pos, AllBlocks.FLUID_TANK.getDefaultState(), 3)
                (level.getBlockEntity(pos) as FluidTankBlockEntity).initialize()
            }
            (level.getBlockEntity(from) as FluidTankBlockEntity).tankInventory.fill(FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE)
            val pump = pumpPos(lane)
            val motor = if (lane == 2) pump.north() else pump.north().west()
            if (lane != 2) level.setBlock(pump.north(), AllBlocks.COGWHEEL.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3)
            level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(PumpBlock.FACING, if (lane == 2) Direction.SOUTH else Direction.EAST), 3)
            (level.getBlockEntity(motor) as CreativeMotorBlockEntity).also {
                it.initialize()
                it.generatedSpeed.setValue(32)
            }
        }
        return "three tank-to-tank networks prepared"
    }

    private fun sample(level: ServerLevel): String = lengths.indices.joinToString(";") { lane ->
        val source = level.getBlockEntity(start(lane)) as FluidTankBlockEntity
        val destination = level.getBlockEntity(output(lane)) as FluidTankBlockEntity
        "$lane=${source.tankInventory.fluidAmount},${destination.tankInventory.fluidAmount}"
    }

    private fun disconnect(level: ServerLevel): String {
        for (lane in lengths.indices) level.removeBlock(start(lane).east(), false)
        return sample(level)
    }

    private fun restore(level: ServerLevel): String {
        // A headless server has no player keeping the distant long-range lane entity-ticking.
        force(level)
        for (lane in lengths.indices) {
            pipe(level, start(lane).east())
            val source = level.getBlockEntity(start(lane)) as FluidTankBlockEntity
            source.tankInventory.fill(FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE)
        }
        return sample(level)
    }

    private fun status(level: ServerLevel): String = "time=${level.gameTime} forced=${level.forcedChunks.size}; " +
        lengths.indices.joinToString("; ") { lane ->
            val pump = level.getBlockEntity(pumpPos(lane)) as PumpBlockEntity
            val transport = FluidPropagator.getPipe(level, pump.blockPos)
            val source = level.getBlockEntity(start(lane)) as FluidTankBlockEntity
            val destination = level.getBlockEntity(output(lane)) as FluidTankBlockEntity
            "$lane speed=${pump.speed}/${pump.theoreticalSpeed} facing=${pump.blockState.getValue(PumpBlock.FACING)} " +
                "phase=${transport.phase} flags=${flags(pump)} source=${source.tankInventory.fluidAmount}/${source.tankInventory.capacity} " +
                "dest=${destination.tankInventory.fluidAmount}/${destination.tankInventory.capacity} " +
                "pressure=${transport.interfaces.mapValues { (_, p) -> p.pressure.toString() + ":flow=" + p.hasFlow() + ":net=" + p.hasNetwork() }}"
        }
}
