package de.TeutonStudio.TeutonLib.integration.energy

import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.TeutonLib
import java.util.Optional
import java.util.function.Supplier
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.fml.ModList
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.energy.IEnergyStorage

/** Optional integration using registry IDs and FE only; no New Age or Flux classes are linked.  */
object NewAgeMotorEnergy {
    private val MOTORS: MutableSet<ResourceLocation> =
        java.util.Set.of<ResourceLocation>(id("basic_motor"), id("advanced_motor"), id("reinforced_motor"))
    private val EXTENSIONS: MutableSet<ResourceLocation> =
        java.util.Set.of<ResourceLocation>(id("basic_motor_extension"), id("advanced_motor_extension"))

    @JvmStatic
    fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        if (!ModList.get().isLoaded("create_new_age")) return
        for (id in EXTENSIONS) {
            val block = BuiltInRegistries.BLOCK.getOptional(id)
            if (block.isEmpty()) {
                TeutonLib.Companion.LOGGER.warn("TeutonLib: motor energy input unavailable for missing block {}", id)
                continue
            }
            // Always return a view, even while disabled or disconnected. Capability caches must
            // be able to observe a later config reload or motor placement without re-querying.
            event.registerBlock<IEnergyStorage, Direction?>(
                Capabilities.EnergyStorage.BLOCK,
                IBlockCapabilityProvider { level: Level, pos: BlockPos, state: BlockState, entity: BlockEntity?, side: Direction? ->
                    if (entity == null || level!!.isClientSide())
                        null
                    else
                        MotorEnergyInput(Supplier { resolveMotor(entity) })
                }, block.get()
            )
        }
    }

    @JvmStatic
    private fun resolveMotor(extension: BlockEntity): IEnergyStorage? {
        if (!ServerConfig.SPEC.isLoaded() || !ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.asBoolean) return null
        val level = extension.level
        val pos = extension.blockPos
        if (level == null || level.isClientSide() || extension.isRemoved() || !level.hasChunkAt(pos) || level.getBlockEntity(
                pos
            ) !== extension
        ) return null
        val state = level.getBlockState(pos)
        if (!EXTENSIONS.contains(BuiltInRegistries.BLOCK.getKey(state.block))
            || !state.hasProperty<Direction>(BlockStateProperties.FACING)
        ) return null
        val facing: Direction = state.getValue<Direction>(BlockStateProperties.FACING)
        val motorPos = pos.relative(facing)
        if (!level.hasChunkAt(motorPos)) return null
        val motor = level.getBlockState(motorPos)
        if (!MOTORS.contains(BuiltInRegistries.BLOCK.getKey(motor.block)) || !motor.hasProperty<Direction>(
                BlockStateProperties.FACING
            ) || motor.getValue<Direction>(BlockStateProperties.FACING) != facing
        ) return null
        val entity = level.getBlockEntity(motorPos)
        if (entity == null || entity.isRemoved()) return null
        return level.getCapability<IEnergyStorage, Direction?>(
            Capabilities.EnergyStorage.BLOCK,
            motorPos,
            facing.opposite
        )
    }

    /** True when a Flux Point side is backed by a live New Age motor extension. */
    @JvmStatic
    fun isMotorExtensionConnection(level: Level, pointPos: BlockPos, side: Direction): Boolean {
        if (!ModList.get().isLoaded("create_new_age") || !ServerConfig.SPEC.isLoaded() || !ServerConfig.MOTOR_EXTENSION_ENERGY_TRANSFER.asBoolean) return false
        val extensionPos = pointPos.relative(side)
        val extension = level.getBlockState(extensionPos)
        if (!EXTENSIONS.contains(BuiltInRegistries.BLOCK.getKey(extension.block)) ||
            !extension.hasProperty<Direction>(BlockStateProperties.FACING)
        ) return false
        val facing = extension.getValue<Direction>(BlockStateProperties.FACING)
        val motorPos = extensionPos.relative(facing)
        val motor = level.getBlockState(motorPos)
        if (!MOTORS.contains(BuiltInRegistries.BLOCK.getKey(motor.block)) ||
            !motor.hasProperty<Direction>(BlockStateProperties.FACING) ||
            motor.getValue<Direction>(BlockStateProperties.FACING) != facing
        ) return false
        if (level.isClientSide()) return true
        val entity = level.getBlockEntity(extensionPos) ?: return false
        return resolveMotor(entity) != null
    }

    @JvmStatic
    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("create_new_age", path)
    }
}
