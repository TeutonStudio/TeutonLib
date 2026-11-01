package de.TeutonStudio.TeutonLib.mixin.hose

import com.simibubi.create.content.fluids.FlowSource
import com.simibubi.create.content.fluids.FluidPropagator
import com.simibubi.create.content.fluids.PipeConnection
import de.TeutonStudio.TeutonLib.integration.hose.HosePipeGraph
import java.util.Optional
import java.util.function.Predicate
import net.createmod.catnip.math.BlockFace
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.neoforged.neoforge.fluids.FluidStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(PipeConnection::class, remap = false)
abstract class HosePipeConnectionMixin {
    @Shadow lateinit var side: Direction
    @Shadow lateinit var source: Optional<FlowSource>

    @Inject(method = ["determineSource"], at = [At("HEAD")], cancellable = true)
    private fun `teutonlib$virtualSource`(world: Level, pos: BlockPos, cir: CallbackInfoReturnable<Boolean>) {
        if (HosePipeGraph.physicalEntryBlocked(world, pos, side)) {
            source = Optional.of(FlowSource.Blocked(BlockFace(pos, side)))
            cir.returnValue = true
            return
        }
        if (!HosePipeGraph.isVirtual(world, pos, side)) return
        val edgeSide = side
        source = Optional.of(object : FlowSource(BlockFace(pos, edgeSide)) {
            override fun isEndpoint() = false
            override fun provideFluid(predicate: Predicate<FluidStack>): FluidStack {
                val remote = HosePipeGraph.remote(world, pos, edgeSide) ?: return FluidStack.EMPTY
                val fluid = FluidPropagator.getPipe(world, remote)?.getProvidedOutwardFluid(edgeSide.opposite) ?: return FluidStack.EMPTY
                return if (predicate.test(fluid)) fluid else FluidStack.EMPTY
            }
        })
        cir.returnValue = true
    }
}
