package de.TeutonStudio.TeutonLib.integration.energy

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Pixel-space geometry shared by the Flux model and its selection/collision shape. */
object MotorExtensionConnectionGeometry {
    private val horizontal = listOf(
        AABB(3.0,3.0,0.0,13.0,13.0,3.0), AABB(2.0,5.0,4.0,14.0,11.0,10.0),
        AABB(5.0,4.0,4.0,11.0,14.0,10.0), AABB(3.0,3.0,3.0,13.0,13.0,11.0),
        AABB(4.0,4.0,11.0,12.0,12.0,14.0), AABB(1.01,.01,3.01,14.99,2.99,6.99))
    private val vertical = listOf(
        AABB(3.0,13.0,3.0,13.0,16.0,13.0), AABB(2.0,6.0,5.0,14.0,12.0,11.0),
        AABB(5.0,6.0,2.0,11.0,12.0,14.0), AABB(3.0,5.0,3.0,13.0,13.0,13.0),
        AABB(4.0,2.0,4.0,12.0,5.0,12.0))

    private fun map(box: AABB, transform: (Vec3) -> Vec3): AABB {
        val points = listOf(box.minX,box.maxX).flatMap { x -> listOf(box.minY,box.maxY).flatMap { y ->
            listOf(box.minZ,box.maxZ).map { z -> transform(Vec3(x,y,z)) } } }
        return AABB(points.minOf { it.x },points.minOf { it.y },points.minOf { it.z },
            points.maxOf { it.x },points.maxOf { it.y },points.maxOf { it.z })
    }
    private fun extension(facing: Direction): List<AABB> = when (facing) {
        Direction.UP -> vertical
        Direction.DOWN -> vertical.map { box -> map(box) { Vec3(it.x,16-it.y,16-it.z) } }
        else -> horizontal.map { box -> map(box) { when (facing) {
            Direction.EAST -> Vec3(16-it.z,it.y,it.x)
            Direction.SOUTH -> Vec3(16-it.x,it.y,16-it.z)
            Direction.WEST -> Vec3(it.z,it.y,16-it.x)
            else -> it
        } } }
    }
    // The canonical arm points down. The neighboring extension occupies y=-16..0.
    private fun canonical(point: Vec3, side: Direction): Vec3 = when (side) {
        Direction.DOWN -> point
        Direction.UP -> Vec3(point.x,16-point.y,point.z)
        Direction.NORTH -> Vec3(point.x,point.z,point.y)
        Direction.SOUTH -> Vec3(point.x,16-point.z,point.y)
        Direction.WEST -> Vec3(point.y,point.x,point.z)
        Direction.EAST -> Vec3(point.y,16-point.x,point.z)
    }
    private fun world(point: Vec3, side: Direction): Vec3 = when (side) {
        Direction.DOWN -> point
        Direction.UP -> Vec3(point.x,16-point.y,point.z)
        Direction.NORTH -> Vec3(point.x,point.z,point.y)
        Direction.SOUTH -> Vec3(point.x,point.z,16-point.y)
        Direction.WEST -> Vec3(point.y,point.x,point.z)
        Direction.EAST -> Vec3(16-point.y,point.x,point.z)
    }
    private val cache = java.util.concurrent.ConcurrentHashMap<Pair<Direction,Direction?>,List<AABB>>()

    /** Null is an ordinary connection. A specialized flange follows the stepped casing,
     * including the horizontal extension's foot, instead of stopping at the block boundary. */
    fun arm(side: Direction, facing: Direction?): List<AABB> = cache.getOrPut(side to facing) {
        val canonicalBoxes = if (facing == null) listOf(AABB(7.0,1.0,7.0,9.0,6.0,9.0),AABB(5.0,0.0,5.0,11.0,1.0,11.0))
        else {
            val offset = Vec3(side.stepX*16.0,side.stepY*16.0,side.stepZ*16.0)
            val surface = extension(facing).map { box -> map(box.move(offset)) { canonical(it,side) } }
            val xs = (listOf(5.0,7.0,9.0,11.0)+surface.flatMap { listOf(it.minX,it.maxX) }).filter { it in 5.0..11.0 }.distinct().sorted()
            val zs = (listOf(5.0,7.0,9.0,11.0)+surface.flatMap { listOf(it.minZ,it.maxZ) }).filter { it in 5.0..11.0 }.distinct().sorted()
            val plates = mutableListOf<AABB>()
            for ((x0,x1) in xs.zipWithNext()) for ((z0,z1) in zs.zipWithNext()) {
                val x=(x0+x1)/2; val z=(z0+z1)/2
                val face=surface.filter { x>it.minX && x<it.maxX && z>it.minZ && z<it.maxZ }.maxOf { it.maxY }
                plates += AABB(x0,face,z0,x1,face+1,z1)
            }
            val end=plates.filter { it.maxX>7 && it.minX<9 && it.maxZ>7 && it.minZ<9 }.minOf { it.maxY }
            var merged=plates.toList()
            for (axis in listOf(Direction.Axis.Z,Direction.Axis.X)) {
                val result=mutableListOf<AABB>()
                for (box in merged) {
                    val last=result.lastOrNull()
                    val adjacent=last != null && last.minY==box.minY && last.maxY==box.maxY &&
                        if (axis==Direction.Axis.Z) last.minX==box.minX && last.maxX==box.maxX && last.maxZ==box.minZ
                        else last.minZ==box.minZ && last.maxZ==box.maxZ && last.maxX==box.minX
                    if (adjacent) result[result.lastIndex]=last!!.minmax(box) else result+=box
                }
                merged=result
            }
            listOf(AABB(7.0,end,7.0,9.0,6.0,9.0))+merged
        }
        canonicalBoxes.map { box -> map(box) { world(it,side) } }
    }
}
