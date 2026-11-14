package de.TeutonStudio.TeutonLib.integration.sable

import de.TeutonStudio.TeutonLib.integration.hose.HoseCableMath
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import net.minecraft.world.phys.Vec3

/** Uniform arc spacing avoids starting the native chain with compressed and overstretched edges. */
internal object HoseCableSeed {
    fun points(head: HoseHeadEntity, saved: List<Vec3>?, start: Vec3, end: Vec3, length: Double, count: Int): List<Vec3> {
        var path=saved?.toMutableList() ?: mutableListOf(start,end)
        path[0]=start; path[path.lastIndex]=end
        // A collision correction or winch resize must retain the existing bend and slack.
        // Replacing an overstretched saved pose with a fresh sag curve teleports the strand
        // across obstacles and repeatedly injects new motion into an otherwise resting rope.
        if (saved == null) {
            val baseline=HoseCableRouting.taut(head,path)
            val direct=HoseCableMath.length(baseline)
            val slack=(length-direct).coerceAtLeast(0.0)
            val seeded=mutableListOf(start)
            for ((a,b) in baseline.zipWithNext()) {
                val leg=a.distanceTo(b)
                val goal=leg+if (direct>1e-9) slack*leg/direct else slack
                val samples=kotlin.math.ceil(goal*8).toInt().coerceIn(16,1024)
                fun curve(sag: Double)=(0..samples).map { index ->
                    val t=index.toDouble()/samples
                    a.lerp(b,t).add(0.0,-sag*kotlin.math.sin(Math.PI*t),0.0)
                }
                var low=0.0; var high=length.coerceAtLeast(.01)
                repeat(24) { val middle=(low+high)*.5; if (HoseCableMath.length(curve(middle))<goal) low=middle else high=middle }
                curve((low+high)*.5).drop(1).forEachIndexed { index,point ->
                    val chord=a.lerp(b,(index+1).toDouble()/samples)
                    seeded.add(HoseCableCollisions.seed(head,chord,point))
                }
            }
            path=seeded
        }
        val cumulative=mutableListOf(0.0)
        path.zipWithNext().forEach { (a,b) -> cumulative.add(cumulative.last()+a.distanceTo(b)) }
        val first=(length-(count-1)).coerceAtLeast(.001)
        return (0..count).map { index ->
            val fraction=if (index == 0) 0.0 else (first+index-1)/length
            val distance=cumulative.last()*fraction.coerceIn(0.0,1.0)
            val edge=cumulative.indexOfLast { it<=distance }.coerceIn(0,path.lastIndex-1)
            val span=cumulative[edge+1]-cumulative[edge]
            path[edge].lerp(path[edge+1],if (span>1e-9) (distance-cumulative[edge])/span else 0.0)
        }
    }
}
