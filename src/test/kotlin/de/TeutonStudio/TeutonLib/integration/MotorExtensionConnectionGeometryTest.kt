package de.TeutonStudio.TeutonLib.integration

import com.google.gson.JsonParser
import de.TeutonStudio.TeutonLib.integration.energy.MotorExtensionConnectionGeometry
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.jar.JarFile
import kotlin.math.*

class MotorExtensionConnectionGeometryTest {
    @Test fun `flange patches touch the original New Age resource models on every accessible face`() {
        JarFile(System.getProperty("teutonlib.newAgeJar")).use { jar ->
            fun json(name: String) = JsonParser.parseString(jar.getInputStream(jar.getJarEntry("assets/create_new_age/$name.json")).reader().readText()).asJsonObject
            for (type in listOf("basic","advanced")) for (facing in Direction.entries) {
                val variant=json("blockstates/${type}_motor_extension").getAsJsonObject("variants").getAsJsonObject("facing=${facing.serializedName}")
                val child=json("models/"+variant["model"].asString.substringAfter(':'))
                val source=json("models/"+child["parent"].asString.substringAfter(':'))
                val ax=Math.toRadians(variant["x"]?.asDouble ?: 0.0); val ay=Math.toRadians(variant["y"]?.asDouble ?: 0.0)
                val surfaces=source.getAsJsonArray("elements").map { element ->
                    val e=element.asJsonObject
                    val from=e.getAsJsonArray("from").map { it.asDouble }; val to=e.getAsJsonArray("to").map { it.asDouble }
                    val corners=(0..7).map { bits ->
                        val x=(if (bits and 1 == 0) from[0] else to[0])-8
                        val y=(if (bits and 2 == 0) from[1] else to[1])-8
                        val z=(if (bits and 4 == 0) from[2] else to[2])-8
                        val yy=y*cos(ax)+z*sin(ax); val zz=-y*sin(ax)+z*cos(ax)
                        listOf(x*cos(ay)-zz*sin(ay)+8,yy+8,x*sin(ay)+zz*cos(ay)+8)
                    }
                    AABB(corners.minOf { it[0] },corners.minOf { it[1] },corners.minOf { it[2] },
                        corners.maxOf { it[0] },corners.maxOf { it[1] },corners.maxOf { it[2] })
                }
                for (side in Direction.entries.filter { it != facing.opposite }) {
                    val axis=side.axis.ordinal; val cross=(0..2).filter { it != axis }
                    val offset=listOf(side.stepX*16.0,side.stepY*16.0,side.stepZ*16.0)
                    fun low(b: AABB)=listOf(b.minX,b.minY,b.minZ)
                    fun high(b: AABB)=listOf(b.maxX,b.maxY,b.maxZ)
                    for (patch in MotorExtensionConnectionGeometry.arm(side,facing).drop(1)) {
                        val center=(0..2).map { (low(patch)[it]+high(patch)[it])/2-offset[it] }
                        val hits=surfaces.filter { b -> cross.all { center[it]>low(b)[it]+1e-8 && center[it]<high(b)[it]-1e-8 } }
                        assertTrue(hits.isNotEmpty(),"No casing under $type/$facing/$side $patch")
                        val negative=side.axisDirection==Direction.AxisDirection.NEGATIVE
                        val actual=if (negative) low(patch)[axis] else high(patch)[axis]
                        val expected=offset[axis]+if (negative) hits.maxOf { high(it)[axis] } else hits.minOf { low(it)[axis] }
                        assertEquals(expected,actual,1e-7,"Floating or embedded flange $type/$facing/$side $patch")
                    }
                }
            }
        }
    }
}
