package de.TeutonStudio.TeutonLib.integration.hose

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Collision-free box-edge routing with a preference for the existing contact side. */
internal object HoseCableContacts {
    fun route(box: AABB, start: Vec3, end: Vec3, previous: List<Vec3>, world: (Vec3) -> Vec3 = { it }): List<Vec3>? {
        val inside=box.deflate(.0001)
        val nodes=mutableListOf(start,end)
        val contacts=linkedSetOf<Vec3>()
        for (x in listOf(box.minX,box.maxX)) for (y in listOf(box.minY,box.maxY)) for (z in listOf(box.minZ,box.maxZ)) contacts.add(Vec3(x,y,z))
        // Box corners alone force a planar hose sideways to a voxel corner. Include edge
        // contacts at the endpoints' coordinates so a strand can pass straight over a wall.
        for (point in listOf(start,end,start.lerp(end,.5))) {
            for (x in listOf(box.minX,box.maxX)) for (y in listOf(box.minY,box.maxY))
                contacts.add(Vec3(x,y,point.z.coerceIn(box.minZ,box.maxZ)))
            for (x in listOf(box.minX,box.maxX)) for (z in listOf(box.minZ,box.maxZ))
                contacts.add(Vec3(x,point.y.coerceIn(box.minY,box.maxY),z))
            for (y in listOf(box.minY,box.maxY)) for (z in listOf(box.minZ,box.maxZ))
                contacts.add(Vec3(point.x.coerceIn(box.minX,box.maxX),y,z))
        }
        nodes.addAll(contacts.filter { it != start && it != end })
        val previousEdges=previous.zipWithNext()
        // Near-equal shortest routes around opposite faces used to swap on tiny solver errors.
        // Prefer contacts close to the previous strand, while still allowing a shorter route
        // to take over when the endpoints or the obstacle actually move.
        val preference=nodes.mapIndexed { index,node ->
            if (index < 2) 0.0 else {
                val point=world(node)
                previousEdges.minOfOrNull { (from,to) ->
                    val delta=to.subtract(from)
                    val t=if (delta.lengthSqr()>1e-12) point.subtract(from).dot(delta)/delta.lengthSqr() else 0.0
                    point.distanceTo(from.add(delta.scale(t.coerceIn(0.0,1.0))))
                }?.coerceAtMost(1.0)?.times(.15) ?: 0.0
            }
        }
        val distances=DoubleArray(nodes.size) { Double.POSITIVE_INFINITY }; distances[0]=0.0
        val predecessors=IntArray(nodes.size) { -1 }; val visited=BooleanArray(nodes.size)
        repeat(nodes.size) {
            val current=nodes.indices.filter { !visited[it] }.minByOrNull { distances[it] } ?: return@repeat
            if (!distances[current].isFinite()) return null
            visited[current]=true
            for (next in nodes.indices) {
                if (next == current || visited[next]) continue
                val from=nodes[current]; val to=nodes[next]
                if (inside.contains(from) || inside.contains(to) || inside.clip(from,to).isPresent) continue
                val distance=distances[current]+from.distanceTo(to)+preference[next]
                if (distance<distances[next]) { distances[next]=distance; predecessors[next]=current }
            }
        }
        if (!distances[1].isFinite()) return null
        val route=mutableListOf(end); var index=1
        while (index != 0) { index=predecessors[index]; if (index<0) return null; route.add(nodes[index]) }
        return route.asReversed()
    }
}
