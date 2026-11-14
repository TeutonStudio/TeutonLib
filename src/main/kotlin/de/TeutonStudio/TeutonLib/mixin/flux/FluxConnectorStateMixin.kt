package de.TeutonStudio.TeutonLib.mixin.flux

import de.TeutonStudio.TeutonLib.integration.energy.FluxPointConnections
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(targets = ["sonar.fluxnetworks.common.block.FluxConnectorBlock"], remap = false)
abstract class FluxConnectorStateMixin : Block {
    constructor(properties: Properties) : super(properties)

    @Inject(method = ["createBlockStateDefinition"], at = [At("TAIL")])
    private fun `teutonlib$rearStates`(builder: StateDefinition.Builder<Block, BlockState>, ci: CallbackInfo) {
        if (FluxPointConnections.isPoint(this)) builder.add(*FluxPointConnections.REAR.values.toTypedArray())
    }

    @Inject(method = ["<init>"], at = [At("RETURN")])
    private fun `teutonlib$defaultRearStates`(properties: Properties, ci: CallbackInfo) {
        if (!FluxPointConnections.isPoint(this)) return
        var state = defaultBlockState()
        for (property in FluxPointConnections.REAR.values) state = state.setValue(property, false)
        registerDefaultState(state)
    }

    @Inject(method = ["getStateForPlacement"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$placeRearState`(context: BlockPlaceContext, cir: CallbackInfoReturnable<BlockState>) {
        cir.returnValue?.let { cir.returnValue = FluxPointConnections.update(it, context.level, context.clickedPos) }
    }
}
