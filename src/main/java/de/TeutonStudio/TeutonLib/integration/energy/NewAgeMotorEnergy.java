package de.TeutonStudio.TeutonLib.integration.energy;

import de.TeutonStudio.TeutonLib.ServerConfig;
import de.TeutonStudio.TeutonLib.TeutonLib;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Optional integration using registry IDs and FE only; no New Age or Flux classes are linked. */
public final class NewAgeMotorEnergy {
    private static final Set<ResourceLocation> MOTORS = Set.of(id("basic_motor"), id("advanced_motor"), id("reinforced_motor"));
    private static final Set<ResourceLocation> EXTENSIONS = Set.of(id("basic_motor_extension"), id("advanced_motor_extension"));

    private NewAgeMotorEnergy() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (!ModList.get().isLoaded("create_new_age")) return;
        for (var id : EXTENSIONS) {
            var block = BuiltInRegistries.BLOCK.getOptional(id);
            if (block.isEmpty()) {
                TeutonLib.LOGGER.warn("TeutonLib: motor energy input unavailable for missing block {}", id);
                continue;
            }
            // Always return a view, even while disabled or disconnected. Capability caches must
            // be able to observe a later config reload or motor placement without re-querying.
            event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                    (level, pos, state, entity, side) -> entity == null || level.isClientSide() ? null
                            : new MotorEnergyInput(() -> resolveMotor(entity)), block.get());
        }
    }

    private static IEnergyStorage resolveMotor(BlockEntity extension) {
        if (!ServerConfig.SPEC.isLoaded() || !ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.getAsBoolean()) return null;
        var level = extension.getLevel();
        var pos = extension.getBlockPos();
        if (level == null || level.isClientSide() || extension.isRemoved() || !level.hasChunkAt(pos)
                || level.getBlockEntity(pos) != extension) return null;
        var state = level.getBlockState(pos);
        if (!EXTENSIONS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                || !state.hasProperty(BlockStateProperties.FACING)) return null;
        var facing = state.getValue(BlockStateProperties.FACING);
        var motorPos = pos.relative(facing);
        if (!level.hasChunkAt(motorPos)) return null;
        var motor = level.getBlockState(motorPos);
        if (!MOTORS.contains(BuiltInRegistries.BLOCK.getKey(motor.getBlock()))
                || !motor.hasProperty(BlockStateProperties.FACING)
                || motor.getValue(BlockStateProperties.FACING) != facing) return null;
        var entity = level.getBlockEntity(motorPos);
        if (entity == null || entity.isRemoved()) return null;
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, motorPos, facing.getOpposite());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("create_new_age", path);
    }
}
