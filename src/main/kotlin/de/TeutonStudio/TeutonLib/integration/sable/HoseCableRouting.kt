package de.TeutonStudio.TeutonLib.integration.sable

import de.TeutonStudio.TeutonLib.integration.hose.HoseCableMath
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Remove freely moving slack from the *load-bearing* path, retaining obstructed bends. Native
 * solver error and gravity sag must not turn an otherwise slack hose into an overloaded spring.
 * Sable patches Level.clip to include moving/rotated sublevels. */
internal object HoseCableRouting {
    private val offsets = listOf(Vec3.ZERO,Vec3(HoseCableMath.RADIUS,0.0,0.0),Vec3(-HoseCableMath.RADIUS,0.0,0.0),
        Vec3(0.0,HoseCableMath.RADIUS,0.0),Vec3(0.0,-HoseCableMath.RADIUS,0.0),
        Vec3(0.0,0.0,HoseCableMath.RADIUS),Vec3(0.0,0.0,-HoseCableMath.RADIUS))
    fun taut(head: HoseHeadEntity, points: List<Vec3>): List<Vec3> {
        if (points.size < 3) return points
        val path=mutableListOf(points.first())
        var current=0
        while (current < points.lastIndex) {
            var next=points.lastIndex
            while (next > current+1 && !clear(head,points[current],points[next])) next--
            path.add(points[next]); current=next
        }
        return path
    }
    private fun clear(head: HoseHeadEntity, a: Vec3, b: Vec3): Boolean {
        val level=head.level()
        val steps=kotlin.math.ceil(a.distanceTo(b)).toInt().coerceIn(1,1024)
        if ((0..steps).any { !level.hasChunkAt(BlockPos.containing(a.lerp(b,it.toDouble()/steps))) }) return false
        return offsets.all { offset ->
            level.clip(ClipContext(a.add(offset),b.add(offset),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,head)).type == HitResult.Type.MISS
        }
    }
}
