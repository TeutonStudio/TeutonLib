package de.TeutonStudio.TeutonLib.integration.sable

import de.TeutonStudio.TeutonLib.integration.hose.HoseCableContacts
import de.TeutonStudio.TeutonLib.integration.hose.HoseCableMath
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.companion.math.BoundingBox3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Sable's rope uses metre-spaced colliding nodes. Add capsule clearance and corner contacts for
 * the edges between those nodes; contacts feed both the transmitted pose and endpoint loading. */
internal object HoseCableCollisions {
    private const val CLEARANCE = HoseCableMath.RADIUS + .005
    private class Collider(val box: AABB, val body: ServerSubLevel?) {
        fun local(point: Vec3) = body?.logicalPose()?.transformPositionInverse(point) ?: point
        fun world(point: Vec3) = body?.logicalPose()?.transformPosition(point) ?: point
    }
    private val offsets = listOf(Vec3.ZERO,Vec3(CLEARANCE,0.0,0.0),Vec3(-CLEARANCE,0.0,0.0),
        Vec3(0.0,CLEARANCE,0.0),Vec3(0.0,-CLEARANCE,0.0),Vec3(0.0,0.0,CLEARANCE),Vec3(0.0,0.0,-CLEARANCE))

    /** Seed slack from an unobstructed chord, rather than spawning it under a floor. */
    fun seed(head: HoseHeadEntity, chord: Vec3, sag: Vec3): Vec3 {
        val hit=head.level().clip(ClipContext(chord,sag,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,head))
        if (hit.type == HitResult.Type.MISS) return sag
        val body=Sable.HELPER.getContaining(head.level(),hit.blockPos) as? ServerSubLevel
        val normal=Vec3.atLowerCornerOf(hit.direction.normal)
        val outward=body?.logicalPose()?.transformNormal(normal)?.normalize() ?: normal
        val location=Sable.HELPER.projectOutOfSubLevel(head.level(),hit.location)
        return location.add(outward.scale(CLEARANCE+.002))
    }
    data class Resolution(val points: List<Vec3>, val displaced: Boolean)
    fun resolve(head: HoseHeadEntity, native: List<Vec3>, previous: List<Vec3>): Resolution {
        val points=native.toMutableList()
        for (index in 1 until points.lastIndex) points[index]=project(head,points[index])
        val output=mutableListOf(points.first())
        fun edge(a: Vec3,b: Vec3,depth: Int) {
            if (output.size >= HoseCableMath.MAX_POINTS-1) return
            val collider=hit(head,a,b)
            val detour=collider?.let { around(it,a,b,previous) }?.mapIndexed { index,point ->
                if (index == 0 || point == b) point else project(head,point)
            }
            if (detour != null && detour.size > 2 && depth < 8) {
                for ((from,to) in detour.zipWithNext()) edge(from,to,depth+1)
            } else output.add(b)
        }
        for ((a,b) in points.zipWithNext()) edge(a,b,0)
        return Resolution(if (output.last().distanceToSqr(points.last())<1e-6) output else native,
            points.zip(native).any { (a,b) -> a.distanceToSqr(b)>.01 })
    }
    private fun colliders(head: HoseHeadEntity, point: Vec3): List<Collider> {
        val candidates=mutableListOf<Collider>()
        fun collect(local: Vec3,body: ServerSubLevel?) {
            val scale=body?.logicalPose()?.scale()
            val radius=if (scale == null) CLEARANCE+.01 else (CLEARANCE+.01)/minOf(scale.x(),scale.y(),scale.z()).coerceAtLeast(.001)
            val low=BlockPos.containing(local.add(-radius,-radius,-radius)); val high=BlockPos.containing(local.add(radius,radius,radius))
            for (pos in BlockPos.betweenClosed(low,high)) {
                if (!head.level().hasChunkAt(pos)) continue
                val state=head.level().getBlockState(pos)
                if (state.isAir) continue
                for (box in state.getCollisionShape(head.level(),pos).toAabbs()) {
                    val expanded=box.move(pos).inflate(radius)
                    if (expanded.contains(local)) candidates.add(Collider(expanded,body))
                }
            }
        }
        collect(point,null)
        val bounds=BoundingBox3d().set(point.x-CLEARANCE,point.y-CLEARANCE,point.z-CLEARANCE,
            point.x+CLEARANCE,point.y+CLEARANCE,point.z+CLEARANCE)
        for (sub in Sable.HELPER.getAllIntersecting(head.level(),bounds)) if (sub is ServerSubLevel)
            collect(sub.logicalPose().transformPositionInverse(point),sub)
        return candidates
    }
    private fun project(head: HoseHeadEntity, point: Vec3): Vec3 {
        val candidates=colliders(head,point)
        if (candidates.isEmpty()) return point
        val options=mutableListOf<Vec3>()
        for (collider in candidates) {
            val local=collider.local(point); val box=collider.box
            options.addAll(listOf(Vec3(box.minX-.001,local.y,local.z),Vec3(box.maxX+.001,local.y,local.z),
                Vec3(local.x,box.minY-.001,local.z),Vec3(local.x,box.maxY+.001,local.z),
                Vec3(local.x,local.y,box.minZ-.001),Vec3(local.x,local.y,box.maxZ+.001)).map(collider::world))
        }
        // Reject internal faces between adjacent solid voxels, rather than oscillating between them.
        for (option in options.sortedBy { it.distanceToSqr(point) }) if (colliders(head,option).isEmpty()) return option
        for (step in 1..16) for (axis in listOf(Vec3(1.0,0.0,0.0),Vec3(-1.0,0.0,0.0),Vec3(0.0,1.0,0.0),
            Vec3(0.0,-1.0,0.0),Vec3(0.0,0.0,1.0),Vec3(0.0,0.0,-1.0))) {
            val option=point.add(axis.scale(step*.25))
            if (colliders(head,option).isEmpty()) return option
        }
        return point
    }
    private fun hit(head: HoseHeadEntity,a: Vec3,b: Vec3): Collider? {
        for (offset in offsets) {
            val result=head.level().clip(ClipContext(a.add(offset),b.add(offset),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,head))
            if (result.type == HitResult.Type.MISS) continue
            val pos=result.blockPos
            if (!head.level().hasChunkAt(pos)) continue
            val shape=head.level().getBlockState(pos).getCollisionShape(head.level(),pos)
            if (shape.isEmpty) continue
            val body=Sable.HELPER.getContaining(head.level(),pos) as? ServerSubLevel
            val scale=body?.logicalPose()?.scale()
            val radius=if (scale == null) CLEARANCE else CLEARANCE/minOf(scale.x(),scale.y(),scale.z()).coerceAtLeast(.001)
            val localA=body?.logicalPose()?.transformPositionInverse(a) ?: a
            val localB=body?.logicalPose()?.transformPositionInverse(b) ?: b
            val box=shape.toAabbs().map { it.move(pos).inflate(radius+.002) }
                .filter { it.deflate(.0001).clip(localA,localB).isPresent }
                .minByOrNull { it.clip(localA,localB).orElse(localA).distanceToSqr(localA) } ?: continue
            return Collider(box,body)
        }
        return null
    }
    private fun around(collider: Collider,a: Vec3,b: Vec3,previous: List<Vec3>): List<Vec3>? =
        HoseCableContacts.route(collider.box,collider.local(a),collider.local(b),previous,collider::world)?.map(collider::world)
}
