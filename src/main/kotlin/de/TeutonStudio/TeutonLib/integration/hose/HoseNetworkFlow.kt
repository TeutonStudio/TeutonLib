package de.TeutonStudio.TeutonLib.integration.hose

import java.util.IdentityHashMap
import net.minecraft.world.level.Level
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction

/** Tentative reservations are local to one native network's SIMULATE/EXECUTE transaction. */
class HoseNetworkFlow {
    private val simulated = IdentityHashMap<HoseDrainConnection, IdentityHashMap<IFluidHandler, Int>>()

    fun clear() = simulated.clear()

    fun fill(world: Level, path: List<HoseDrainConnection>, handler: IFluidHandler,
        fluid: FluidStack, action: FluidAction): Int {
        if (path.any { !it.active() }) return 0
        var allowance = fluid.amount
        for (connector in path) {
            val remaining = connector.budget.available(world.gameTime, HosePipeGraph.nativeRate(connector))
            val reservations = simulated[connector]
            val reserved = if (action.simulate()) reservations?.values?.sum() ?: 0 else 0
            val previous = if (action.simulate()) reservations?.get(handler) ?: 0 else 0
            allowance = minOf(allowance, (remaining - reserved + previous).coerceAtLeast(0))
        }
        if (allowance <= 0) return 0
        val accepted = handler.fill(fluid.copyWithAmount(allowance), action)
        for (connector in path) {
            if (action.execute()) connector.budget.consume(accepted)
            else simulated.getOrPut(connector) { IdentityHashMap() }[handler] = accepted
        }
        return accepted
    }
}
