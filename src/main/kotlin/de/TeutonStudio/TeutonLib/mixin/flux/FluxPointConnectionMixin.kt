package de.TeutonStudio.TeutonLib.mixin.flux

import de.TeutonStudio.TeutonLib.integration.energy.NewAgeMotorEnergy
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Extends the selectable/collision arm to match the longer Flux Point model. */
@Mixin(targets = ["sonar.fluxnetworks.common.block.FluxPointBlock"], remap = false)
abstract class FluxPointConnectionMixin {
    @Inject(method = ["getShape"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$extendConnectionArm`(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
        cir: CallbackInfoReturnable<VoxelShape>
    ) {
        var shape = cir.returnValue
        val world = level as? net.minecraft.world.level.Level ?: return
        if (state.getValue(BlockStateProperties.DOWN) && NewAgeMotorEnergy.isMotorExtensionConnection(world, pos, Direction.DOWN)) {
            shape = Shapes.or(shape, Block.box(7.0, -4.0, 7.0, 9.0, 8.0, 9.0))
        }
        if (state.getValue(BlockStateProperties.UP) && NewAgeMotorEnergy.isMotorExtensionConnection(world, pos, Direction.UP)) {
            shape = Shapes.or(shape, Block.box(7.0, 8.0, 7.0, 9.0, 20.0, 9.0))
        }
        if (state.getValue(BlockStateProperties.NORTH) && NewAgeMotorEnergy.isMotorExtensionConnection(world, pos, Direction.NORTH)) {
            shape = Shapes.or(shape, Block.box(7.0, 7.0, -4.0, 9.0, 9.0, 8.0))
        }
        if (state.getValue(BlockStateProperties.SOUTH) && NewAgeMotorEnergy.isMotorExtensionConnection(world, pos, Direction.SOUTH)) {
            shape = Shapes.or(shape, Block.box(7.0, 7.0, 8.0, 9.0, 9.0, 20.0))
        }
        if (state.getValue(BlockStateProperties.WEST) && NewAgeMotorEnergy.isMotorExtensionConnection(world, pos, Direction.WEST)) {
            shape = Shapes.or(shape, Block.box(-4.0, 7.0, 7.0, 8.0, 9.0, 9.0))
        }
        if (state.getValue(BlockStateProperties.EAST) && NewAgeMotorEnergy.isMotorExtensionConnection(world, pos, Direction.EAST)) {
            shape = Shapes.or(shape, Block.box(8.0, 7.0, 7.0, 20.0, 9.0, 9.0))
        }
        cir.returnValue = shape
    }
}
