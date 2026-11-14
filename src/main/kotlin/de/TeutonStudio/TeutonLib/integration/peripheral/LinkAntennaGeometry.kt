package de.TeutonStudio.TeutonLib.integration.peripheral

import de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Shared render/hit dimensions. The antenna extends out of the selected face. */
object LinkAntennaGeometry {
    /** Allow only the small overlap of the mounting plate, never a click through the host block. */
    fun occluded(obstruction: BlockHitResult, position: BlockPos, start: Vec3, hit: Vec3): Boolean =
        obstruction.type != HitResult.Type.MISS &&
            obstruction.location.distanceToSqr(start) + .001 < hit.distanceToSqr(start) &&
            (obstruction.blockPos != position || obstruction.location.distanceToSqr(hit) > .0025)

    fun boxes(side: Direction): List<AABB> {
        fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): AABB {
            val points = listOf(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ)).map { orient(it, side) }
            return AABB(points[0], points[1])
        }
        return listOf(box(.32, .98, .32, .68, 1.08, .68), box(.46, 1.08, .46, .54, 1.43, .54),
            box(.41, 1.36, .41, .59, 1.46, .59))
    }
    fun orient(point: Vec3, side: Direction): Vec3 {
        val p = point.subtract(.5, .5, .5)
        return when (side) {
            Direction.UP -> p
            Direction.DOWN -> Vec3(p.x, -p.y, -p.z)
            Direction.NORTH -> Vec3(p.x, p.z, -p.y)
            Direction.SOUTH -> Vec3(p.x, -p.z, p.y)
            Direction.EAST -> Vec3(p.y, -p.x, p.z)
            Direction.WEST -> Vec3(-p.y, p.x, p.z)
        }.add(.5, .5, .5)
    }
    /** Nearest antenna intersection, using an affine world/local transform. */
    fun hit(level: Level, pos: BlockPos, side: Direction, start: Vec3, end: Vec3): Vec3? {
        return hit(pos, side, start, end) { SubLevelGeometry.projectToWorld(level, it) }
    }
    internal fun hit(pos: BlockPos, side: Direction, start: Vec3, end: Vec3, project: (Vec3) -> Vec3): Vec3? {
        val origin = Vec3.atLowerCornerOf(pos)
        val projected = project(origin)
        val axes = listOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0)).map {
            project(origin.add(it)).subtract(projected)
        }
        fun local(point: Vec3): Vec3 {
            val delta = point.subtract(projected)
            return Vec3(delta.dot(axes[0]) / axes[0].lengthSqr(), delta.dot(axes[1]) / axes[1].lengthSqr(),
                delta.dot(axes[2]) / axes[2].lengthSqr())
        }
        val localStart = local(start)
        val localEnd = local(end)
        val intersection = boxes(side).mapNotNull { it.clip(localStart, localEnd).orElse(null) }
            .minByOrNull { it.distanceToSqr(localStart) } ?: return null
        return projected.add(axes[0].scale(intersection.x)).add(axes[1].scale(intersection.y)).add(axes[2].scale(intersection.z))
    }
}
