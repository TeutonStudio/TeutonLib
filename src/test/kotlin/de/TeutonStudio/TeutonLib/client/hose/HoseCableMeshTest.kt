package de.TeutonStudio.TeutonLib.client.hose

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.floor

class HoseCableMeshTest {
    private data class Vertex(val x: Float, val y: Float, val z: Float, var u: Float = Float.NaN, var v: Float = Float.NaN)
    private class RecordingConsumer : VertexConsumer {
        val vertices = mutableListOf<Vertex>()
        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply { vertices.add(Vertex(x,y,z)) }
        override fun setUv(u: Float, v: Float): VertexConsumer = apply { vertices.last().let { it.u=u; it.v=v } }
        override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer = this
        override fun setUv1(u: Int, v: Int): VertexConsumer = this
        override fun setUv2(u: Int, v: Int): VertexConsumer = this
        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this
    }
    private fun mesh(points: List<Vec3>): List<Vertex> = RecordingConsumer().also {
        HoseCableMesh.draw(points,Vec3.ZERO,it,{ 0 },{ u -> u },{ v -> v })
    }.vertices

    @Test fun `paying out hose at the pulley leaves the texture fixed on the head`() {
        val short = mesh(listOf(Vec3(0.0,4.25,0.0),Vec3(0.0,3.0,0.0),Vec3(0.0,1.5,0.0),Vec3.ZERO))
        val extended = mesh(listOf(Vec3(0.0,4.75,0.0),Vec3(0.0,3.0,0.0),Vec3(0.0,1.5,0.0),Vec3.ZERO))
        val head = short.filter { it.y <= 1.0f }
        assertTrue(head.isNotEmpty())
        assertEquals(head,extended.filter { it.y <= 1.0f })
        assertTrue(head.filter { it.y == 0f }.all { it.v == 0f })
    }

    @Test fun `a distant bend does not rotate the texture frame at the head`() {
        val tail = listOf(Vec3(0.0,3.0,0.0),Vec3(0.0,1.5,0.0),Vec3.ZERO)
        val straight = mesh(listOf(Vec3(0.0,4.25,0.0))+tail)
        val bent = mesh(listOf(Vec3(-2.0,4.25,1.0),Vec3(-1.0,3.5,.5))+tail)
        assertEquals(straight.filter { it.y <= 1.0f },bent.filter { it.y <= 1.0f })
    }

    @Test fun `contact nodes and texture seams preserve metre spacing from the head`() {
        val vertices = mesh(listOf(Vec3(0.0,3.27,0.0),Vec3(0.0,1.61,0.0),Vec3(0.0,.43,0.0),Vec3.ZERO,Vec3.ZERO))
        assertTrue(vertices.isNotEmpty())
        for (vertex in vertices) {
            assertTrue(vertex.u in 0f..1f && vertex.v in 0f..1f)
            val phase=vertex.y-floor(vertex.y)
            assertTrue(abs(vertex.v-phase)<1e-6 || (abs(phase)<1e-6 && abs(vertex.v-1f)<1e-6),
                "Texture slid along the hose at $vertex")
        }
    }
}
