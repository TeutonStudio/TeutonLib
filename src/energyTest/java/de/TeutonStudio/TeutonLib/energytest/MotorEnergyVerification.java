package de.TeutonStudio.TeutonLib.energytest;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.TeutonStudio.TeutonLib.ServerConfig;
import de.TeutonStudio.TeutonLib.TeutonLib;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Invoked only in disposable development worlds by tools/verify_server.py. */
@Mod("teutonlib_energy_test")
public final class MotorEnergyVerification {
    private static final BlockPos ORIGIN = new BlockPos(8, 220, 8);
    private static IEnergyStorage cached;
    private static final BlockPos LIFECYCLE_POS = new BlockPos(16008, 220, 16008);
    private static IEnergyStorage lifecycleCached;

    public MotorEnergyVerification() {
        NeoForge.EVENT_BUS.addListener(this::commands);
    }

    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("teutonlib_energy_test")
                .requires(source -> source.hasPermission(4))
                .executes(ctx -> {
                    try {
                        run(ctx.getSource().getLevel());
                        ctx.getSource().sendSuccess(() -> Component.literal("Motor energy verification PASS: 36 assemblies, 252 side queries, FE source, Flux Point, config, replacement"), false);
                        return 1;
                    } catch (Throwable error) {
                        TeutonLib.LOGGER.error("Motor energy verification failed", error);
                        ctx.getSource().sendFailure(Component.literal("FAIL: " + error));
                        return 0;
                    }
                })
                .then(Commands.literal("prepare").executes(ctx -> {
                    var level = ctx.getSource().getLevel();
                    setup(level, "basic_motor_extension", "basic_motor", Direction.EAST);
                    cached = input(level, Direction.WEST);
                    ctx.getSource().sendSuccess(() -> Component.literal("Cached motor energy view prepared"), false);
                    return 1;
                }))
                .then(Commands.literal("lifecycle").then(Commands.argument("action", StringArgumentType.word()).executes(ctx -> {
                    try {
                        String action = StringArgumentType.getString(ctx, "action");
                        boolean passed = lifecycle(ctx.getSource().getLevel(), action);
                        ctx.getSource().sendSuccess(() -> Component.literal("Motor lifecycle " + action + " PASS=" + passed), false);
                        return passed ? 1 : 0;
                    } catch (Throwable error) {
                        TeutonLib.LOGGER.error("Motor lifecycle verification failed", error);
                        ctx.getSource().sendFailure(Component.literal("FAIL: " + error));
                        return 0;
                    }
                })))
                .then(Commands.literal("probe").executes(ctx -> {
                    boolean enabled = ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.getAsBoolean();
                    boolean passed = cached != null && cached.canReceive() == enabled
                            && (cached.receiveEnergy(1, true) == 1) == enabled;
                    ctx.getSource().sendSuccess(() -> Component.literal("Cached energy view: enabled=" + enabled + " PASS=" + passed), false);
                    return passed ? 1 : 0;
                })));
    }

    private static boolean lifecycle(ServerLevel level, String action) throws Exception {
        if (action.equals("unloaded")) {
            if (level.hasChunkAt(LIFECYCLE_POS)) return false;
            require(lifecycleCached != null && !lifecycleCached.canReceive()
                    && lifecycleCached.receiveEnergy(1, false) == 0, "unloaded view rejects input");
            require(!level.hasChunkAt(LIFECYCLE_POS), "energy query must not reload chunk");
            return true;
        }
        level.getChunkAt(LIFECYCLE_POS);
        if (action.equals("prepare")) {
            level.setBlockAndUpdate(LIFECYCLE_POS, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(LIFECYCLE_POS.east(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(LIFECYCLE_POS, state("advanced_motor_extension", Direction.EAST));
            level.setBlockAndUpdate(LIFECYCLE_POS.east(), state("reinforced_motor", Direction.EAST));
            var motor = level.getBlockEntity(LIFECYCLE_POS.east());
            // Keep the motor switched off so its normal consumption cannot affect persistence checks.
            motor.getClass().getField("needsPower").setBoolean(motor, true);
            lifecycleCached = level.getCapability(Capabilities.EnergyStorage.BLOCK, LIFECYCLE_POS, null);
            require(lifecycleCached != null && lifecycleCached.receiveEnergy(23, false) == 23, "prepare saved energy");
            motor.setChanged();
            return true;
        }
        var input = level.getCapability(Capabilities.EnergyStorage.BLOCK, LIFECYCLE_POS, null);
        require(input != null && input.getEnergyStored() == 23, "preserved motor energy");
        require(input.receiveEnergy(1, false) == 1 && input.getEnergyStored() == 24, "restored connection");
        if (action.equals("reload")) require(lifecycleCached != null && !lifecycleCached.canReceive(), "old chunk view stays invalid");
        else require(action.equals("restart"), "known lifecycle action");
        level.setBlockAndUpdate(LIFECYCLE_POS, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(LIFECYCLE_POS.east(), Blocks.AIR.defaultBlockState());
        return true;
    }

    private static void run(ServerLevel level) throws Exception {
        boolean previous = ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.getAsBoolean();
        ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(true);
        level.getChunkAt(ORIGIN);
        try {
            for (String extension : new String[]{"basic_motor_extension", "advanced_motor_extension"}) {
                for (String motor : new String[]{"basic_motor", "advanced_motor", "reinforced_motor"}) {
                    for (Direction direction : Direction.values()) {
                        setup(level, extension, motor, direction);
                        var motorPos = ORIGIN.relative(direction);
                        var storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, motorPos, direction.getOpposite());
                        require(storage != null, "motor FE capability");
                        for (Direction side : Direction.values()) require(input(level, side).canReceive(), "input side " + side);
                        var input = input(level, null);
                        int before = storage.getEnergyStored();
                        require(input.receiveEnergy(41, true) == 41, "simulation accepts");
                        require(storage.getEnergyStored() == before, "simulation preserves energy");
                        var source = new EnergyStorage(1000);
                        source.receiveEnergy(1000, false);
                        int accepted = input.receiveEnergy(source.extractEnergy(41, true), false);
                        source.extractEnergy(accepted, false);
                        require(accepted == 41 && storage.getEnergyStored() == before + 41
                                && source.getEnergyStored() == 959, "FE source accounting");
                        require(input.getEnergyStored() == storage.getEnergyStored()
                                && input.getMaxEnergyStored() == storage.getMaxEnergyStored(), "storage view");
                        require(!input.canExtract() && input.extractEnergy(41, false) == 0, "no extraction");
                        storage.receiveEnergy(Integer.MAX_VALUE, false);
                        require(input.receiveEnergy(1, false) == 0, "full motor");
                        level.setBlockAndUpdate(motorPos, Blocks.AIR.defaultBlockState());
                        require(!input.canReceive() && input.receiveEnergy(1, false) == 0, "removed motor");
                        level.setBlockAndUpdate(motorPos, state(motor, direction.getOpposite()));
                        require(!input.canReceive(), "wrong motor orientation");
                        level.setBlockAndUpdate(motorPos, state(motor, direction));
                        require(input.canReceive() && input.receiveEnergy(7, false) == 7, "motor reconnected");
                        ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(false);
                        require(!input.canReceive() && input.receiveEnergy(1, false) == 0, "cached config disable");
                        ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(true);
                        require(input.canReceive(), "cached config re-enable");
                        level.setBlockAndUpdate(ORIGIN, state(extension, direction.getOpposite()));
                        require(!input.canReceive(), "rotated extension");
                        level.setBlockAndUpdate(ORIGIN, Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(ORIGIN, state(extension, direction));
                        require(!input.canReceive(), "old view cannot attach to replacement extension");
                    }
                }
            }
            setup(level, "basic_motor_extension", "basic_motor", Direction.EAST);
            level.setBlockAndUpdate(ORIGIN.east(), state("basic_motor_extension", Direction.EAST));
            level.setBlockAndUpdate(ORIGIN.east(2), state("basic_motor", Direction.EAST));
            require(!input(level, Direction.WEST).canReceive(), "no extension chains");
            verifyFluxPoint(level);
        } finally {
            ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(previous);
            clear(level);
        }
    }

    private static void verifyFluxPoint(ServerLevel level) throws Exception {
        setup(level, "advanced_motor_extension", "reinforced_motor", Direction.EAST);
        var pointBlock = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("fluxnetworks:flux_point")).orElseThrow();
        level.setBlockAndUpdate(ORIGIN.west(), pointBlock.defaultBlockState());
        BlockEntity point = level.getBlockEntity(ORIGIN.west());
        require(point != null, "Flux Point placed");
        // Run the actual Point's consumer discovery, simulation and delivery cycle. The
        // network-supplied buffer is seeded here so no player/network ownership is needed.
        Object handler = point.getClass().getMethod("getTransferHandler").invoke(point);
        var type = handler.getClass();
        type.getMethod("updateSideTransfer", Direction.class, BlockEntity.class, boolean.class)
                .invoke(handler, Direction.EAST, level.getBlockEntity(ORIGIN), false);
        type.getMethod("onCycleStart").invoke(handler);
        long request = (long) type.getMethod("getRequest").invoke(handler);
        require(request >= 37, "Flux Point requests energy");
        require(input(level, null).getEnergyStored() == 0, "Flux simulation preserves energy");
        type.getMethod("addToBuffer", long.class).invoke(handler, 37L);
        type.getMethod("onCycleEnd").invoke(handler);
        require(input(level, null).getEnergyStored() == 37, "Flux Point delivers exactly 37 FE");
        ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(false);
        type.getMethod("addToBuffer", long.class).invoke(handler, 13L);
        type.getMethod("onCycleStart").invoke(handler);
        type.getMethod("onCycleEnd").invoke(handler);
        ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.set(true);
        require(input(level, null).getEnergyStored() == 37, "disabled Flux delivery preserves motor");
        type.getMethod("onCycleStart").invoke(handler);
        type.getMethod("onCycleEnd").invoke(handler);
        require(input(level, null).getEnergyStored() == 50, "Flux Point resumes and retains undelivered energy");
    }

    private static void setup(ServerLevel level, String extension, String motor, Direction direction) {
        clear(level);
        level.setBlockAndUpdate(ORIGIN, state(extension, direction));
        level.setBlockAndUpdate(ORIGIN.relative(direction), state(motor, direction));
    }

    private static void clear(ServerLevel level) {
        level.setBlockAndUpdate(ORIGIN, Blocks.AIR.defaultBlockState());
        for (Direction direction : Direction.values()) {
            level.setBlockAndUpdate(ORIGIN.relative(direction), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(ORIGIN.relative(direction, 2), Blocks.AIR.defaultBlockState());
        }
    }

    private static BlockState state(String name, Direction facing) {
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create_new_age", name)).orElseThrow();
        return block.defaultBlockState().setValue(BlockStateProperties.FACING, facing);
    }

    private static IEnergyStorage input(ServerLevel level, Direction side) {
        var input = level.getCapability(Capabilities.EnergyStorage.BLOCK, ORIGIN, side);
        require(input != null, "extension capability on " + side);
        return input;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
