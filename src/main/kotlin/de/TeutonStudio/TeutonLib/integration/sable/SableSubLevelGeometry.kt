package de.TeutonStudio.TeutonLib.integration.sable

import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/** The sole server-side adapter from TeutonLib world geometry to Sable Companion. */
internal object SableSubLevelGeometry {
    fun projectToWorld(level: Level, position: Vec3): Vec3 =
        Sable.HELPER.projectOutOfSubLevel(level, position)

    fun trackedBy(player: net.minecraft.server.level.ServerPlayer, block: net.minecraft.world.level.block.entity.BlockEntity): Boolean? {
        val sublevel = Sable.HELPER.getContaining(block) as? dev.ryanhcode.sable.sublevel.ServerSubLevel ?: return null
        return player.uuid in sublevel.trackingPlayers
    }

    fun distanceSquared(level: Level, first: Vec3, second: Vec3): Double =
        Sable.HELPER.distanceSquaredWithSubLevels(level, first, second)
}
