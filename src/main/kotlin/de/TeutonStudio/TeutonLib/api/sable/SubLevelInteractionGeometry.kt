package de.TeutonStudio.TeutonLib.api.sable

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

/** Sable-aware reach and server interaction checks without exposing Sable types. */
object SubLevelInteractionGeometry {
    @JvmStatic
    @JvmOverloads
    fun withinReach(player: Player, level: Level, position: BlockPos, padding: Double = 1.0): Boolean {
        val maximumDistance = player.blockInteractionRange() + padding
        return SubLevelGeometry.distanceSquared(level, player.position(), position.center) <= maximumDistance * maximumDistance
    }

    @JvmStatic
    fun mayInteract(player: Player, level: Level, position: BlockPos): Boolean =
        level.mayInteract(player, BlockPos.containing(SubLevelGeometry.projectToWorld(level, position.center)))
}
