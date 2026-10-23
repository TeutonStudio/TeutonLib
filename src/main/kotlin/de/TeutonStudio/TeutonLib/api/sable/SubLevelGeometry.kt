package de.TeutonStudio.TeutonLib.api.sable

import de.TeutonStudio.TeutonLib.integration.sable.SableSubLevelGeometry
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList

/**
 * World-space geometry for positions which may live in a moving Sable sublevel.
 *
 * This public facade deliberately exposes Minecraft types only. The Sable-backed implementation
 * is reached only after the loader confirms that Sable is present, so callers may use this API in
 * installations without Sable.
 */
object SubLevelGeometry {
    @JvmStatic
    fun projectToWorld(level: Level, position: Vec3): Vec3 =
        if (sableLoaded()) SableSubLevelGeometry.projectToWorld(level, position) else position

    @JvmStatic
    fun distanceSquared(level: Level, first: Vec3, second: Vec3): Double =
        if (sableLoaded()) SableSubLevelGeometry.distanceSquared(level, first, second)
        else first.distanceToSqr(second)

    @JvmStatic
    fun distance(level: Level, first: Vec3, second: Vec3): Double =
        kotlin.math.sqrt(distanceSquared(level, first, second))

    private fun sableLoaded(): Boolean = runCatching { ModList.get().isLoaded("sable") }.getOrDefault(false)
}
