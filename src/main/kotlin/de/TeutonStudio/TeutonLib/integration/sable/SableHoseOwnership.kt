package de.TeutonStudio.TeutonLib.integration.sable

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

/** Distinguishes an unloaded owner plot from one that Sable has permanently released. */
internal object SableHoseOwnership {
    fun isSubLevelOwner(level: Level, owner: BlockPos): Boolean {
        val container = SubLevelContainer.getContainer(level) ?: return false
        // Occupancy survives UNLOADED, unlike getContaining()/getSubLevel().
        return container.inBounds(owner) && !ownerRemoved(level, owner)
    }

    fun ownerRemoved(level: Level, owner: BlockPos): Boolean {
        val container = SubLevelContainer.getContainer(level) ?: return false
        if (!container.inBounds(owner)) return false

        // Sable 2.0.6 getPlot()/inBounds() use chunk coordinates, shift by logPlotSize,
        // then subtract the plot-space origin. getIndex() takes these local plot indices.
        val origin = container.origin
        val plotX = ((owner.x shr 4) shr container.logPlotSize) - origin.x
        val plotZ = ((owner.z shr 4) shr container.logPlotSize) - origin.y
        val index = container.getIndex(plotX, plotZ)

        // removeSubLevel clears occupancy only for REMOVED. UNLOADED retains this bit
        // even though getSubLevel() and getPlot() then both return null.
        return !container.occupancy.get(index)
    }
}
