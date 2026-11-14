package de.TeutonStudio.TeutonLib.client.hose

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import de.TeutonStudio.TeutonLib.integration.hose.HoseCableMath
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

/** Continuous square tube with frames and metre-spaced texture repeats anchored at the head. */
internal object HoseCableMesh {
    fun draw(points: List<Vec3>, camera: Vec3, sprite: TextureAtlasSprite, consumer: VertexConsumer, light: (Vec3)->Int) =
        draw(points,camera,consumer,light,sprite::getU,sprite::getV)

    internal fun draw(path: List<Vec3>, camera: Vec3, consumer: VertexConsumer, light: (Vec3)->Int,
                      textureU: (Float)->Float, textureV: (Float)->Float) {
        if (path.size < 2) return
        // Simulation/network order is pulley -> head. Build both the cross-section frames
        // and UVs in the other direction: paying out hose at the pulley must not scroll the
        // pattern or propagate changes in distant bends down to the head's cross section.
        val points=path.asReversed()
        val corners=ArrayList<List<Vec3>>(points.size)
        var right=Vec3(1.0,0.0,0.0)
        for (index in points.indices) {
            val tangent=points[minOf(index+1,points.lastIndex)].subtract(points[maxOf(index-1,0)]).normalize()
            right=right.subtract(tangent.scale(right.dot(tangent)))
            if (right.lengthSqr()<1e-10) right=tangent.cross(Vec3(0.0,0.0,1.0))
            if (right.lengthSqr()<1e-10) right=Vec3(1.0,0.0,0.0)
            right=right.normalize()
            val side=tangent.cross(right).normalize()
            val r=right.scale(HoseCableMath.RADIUS); val s=side.scale(HoseCableMath.RADIUS); val p=points[index]
            corners.add(listOf(p.subtract(r).subtract(s),p.add(r).subtract(s),p.add(r).add(s),p.subtract(r).add(s)))
        }
        val pose=PoseStack().last()
        var arc=0.0
        for (index in 0 until points.lastIndex) {
            val length=points[index].distanceTo(points[index+1])
            if (!length.isFinite() || length<1e-8) continue
            var from=0.0
            while (from < length-1e-8) {
                val phase=arc-floor(arc+1e-8)
                val to=minOf(length,from+1.0-phase)
                val v0=phase.coerceIn(0.0,1.0).toFloat(); val v1=(phase+to-from).coerceIn(0.0,1.0).toFloat()
                val illumination=light(points[index].lerp(points[index+1],(from+to)*.5/length))
                for (face in 0..3) {
                    val next=(face+1)%4
                    val a=corners[index][face].lerp(corners[index+1][face],from/length)
                    val b=corners[index][next].lerp(corners[index+1][next],from/length)
                    val c=corners[index][next].lerp(corners[index+1][next],to/length)
                    val d=corners[index][face].lerp(corners[index+1][face],to/length)
                    val normal=b.subtract(a).cross(c.subtract(a)).normalize()
                    fun vertex(point: Vec3,u: Float,v: Float) {
                        val relative=point.subtract(camera)
                        consumer.addVertex(pose.pose(),relative.x.toFloat(),relative.y.toFloat(),relative.z.toFloat())
                            .setColor(255,255,255,255).setUv(textureU(u),textureV(v)).setLight(illumination)
                            .setNormal(pose,normal.x.toFloat(),normal.y.toFloat(),normal.z.toFloat())
                    }
                    vertex(a,.75f,v0); vertex(b,1f,v0); vertex(c,1f,v1); vertex(d,.75f,v1)
                }
                arc+=to-from; from=to
            }
        }
    }
}
