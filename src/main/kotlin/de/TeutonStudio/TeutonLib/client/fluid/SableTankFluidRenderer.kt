package de.TeutonStudio.TeutonLib.client.fluid

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import de.TeutonStudio.TeutonLib.integration.fluid.TankFluidGeometry
import de.TeutonStudio.TeutonLib.integration.fluid.TankFluidGeometry.Bounds
import de.TeutonStudio.TeutonLib.integration.fluid.TankFluidGeometry.Face
import de.TeutonStudio.TeutonLib.integration.fluid.TankFluidGeometry.Mesh
import de.TeutonStudio.TeutonLib.integration.fluid.TankFluidGeometry.Point
import dev.ryanhcode.sable.Sable
import net.createmod.catnip.platform.NeoForgeCatnipServices
import net.createmod.catnip.render.FluidRenderHelper
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import org.joml.Vector3d
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/** Keeps a tank's fluid under world gravity while Sable transforms its containing block entity. */
object SableTankFluidRenderer {
    private const val SIDE_INSET = 9.0 / 128.0
    private const val BOTTOM_INSET = 5.0 / 16.0
    private const val TOP_INSET = 1.0 / 4.0
    private const val NORMAL_TOLERANCE = 1e-10
    private const val CLIP_TOLERANCE = 1e-10

    private data class TextureVertex(val point: Point, val u: Double, val v: Double)
    private data class Quad(val vertices: List<TextureVertex>, val normal: Point)
    private data class CachedMesh(
        val bounds: Bounds,
        val fraction: Double,
        val localUp: Point,
        val lighterThanAir: Boolean,
        val mesh: Mesh,
        val quads: List<Quad>,
        val buildCount: Long
    )

    /** Test-only snapshots contain no block entity, level or render-pose references. */
    data class Draw(
        val mesh: Mesh,
        val bounds: Bounds,
        val fraction: Double,
        val localUp: Point,
        val planeWorldYDeviation: Double,
        val meshBuildCount: Long,
        val meshReused: Boolean,
        val renderCallCount: Long
    )

    // Values intentionally never retain their weak keys. CPU geometry is independent of sprite reloads.
    private val meshes = WeakHashMap<FluidTankBlockEntity, CachedMesh>()
    private val draws = if (java.lang.Boolean.getBoolean("teutonlib.tankFluidClientTests"))
        WeakHashMap<FluidTankBlockEntity, Draw>() else null

    @JvmStatic
    fun lastDraw(blockEntity: FluidTankBlockEntity): Draw? = draws?.get(blockEntity)

    /** Called after Create's controller/window guards; false lets the native renderer continue. */
    @JvmStatic
    fun render(
        blockEntity: FluidTankBlockEntity,
        partialTicks: Float,
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        packedLight: Int
    ): Boolean {
        val subLevel = Sable.HELPER.getContainingClient(blockEntity) ?: return false
        val fluidLevel = blockEntity.fluidLevel ?: return true
        val fraction = fluidLevel.getValue(partialTicks).toDouble().coerceIn(0.0, 1.0)
        if (!fraction.isFinite()) return false
        val width = blockEntity.width.toDouble()
        val height = blockEntity.height.toDouble()
        val interiorHeight = height - BOTTOM_INSET - TOP_INSET
        if (width <= 2 * SIDE_INSET || interiorHeight <= 0.0) return false
        val fluid = blockEntity.tankInventory.fluid
        if (fluid.isEmpty || fraction < 1.0 / (512.0 * interiorHeight)) return true

        val renderPose = subLevel.renderPose(partialTicks)
        // World height of local p is (scale * inverse(rotation) * worldUp) dot p + constant.
        // The BE PoseStack already contains that transform, so vertices remain BE-local.
        val gravityUp = renderPose.orientation().transformInverse(Vector3d(0.0, 1.0, 0.0))
            .mul(renderPose.scale())
        val upLength = gravityUp.length()
        if (!upLength.isFinite() || upLength <= 1e-12) return false
        gravityUp.div(upLength)
        val localUp = Point(gravityUp.x, gravityUp.y, gravityUp.z)
        val bounds = Bounds(
            Point(SIDE_INSET, BOTTOM_INSET, SIDE_INSET),
            Point(width - SIDE_INSET, height - TOP_INSET, width - SIDE_INSET)
        )
        val helper = NeoForgeCatnipServices.FLUID_HELPER
        val lighterThanAir = helper.isLighterThanAir(fluid)
        val previous = meshes[blockEntity]
        val reused = previous != null && previous.bounds == bounds && previous.fraction == fraction &&
            previous.lighterThanAir == lighterThanAir && sameNormal(previous.localUp, localUp)
        val cached = if (reused) previous!! else {
            val mesh = TankFluidGeometry.solve(bounds, localUp, fraction, lighterThanAir)
            CachedMesh(bounds, fraction, localUp, lighterThanAir, mesh,
                textureQuads(mesh, lighterThanAir), (previous?.buildCount ?: 0L) + 1L)
                .also { meshes[blockEntity] = it }
        }

        val sprite = helper.getStillTextureOrMissing(fluid)
        val color = helper.getColor(fluid, null, null)
        val luminosity = max((packedLight shr 4) and 15, helper.getLuminosity(fluid))
        val light = (packedLight and 0xF00000) or (luminosity shl 4)
        val consumer = FluidRenderHelper.getFluidBuilder(buffers)
        draw(cached.quads, sprite, color, light, consumer, poseStack.last())

        draws?.let { observations ->
            val surface = cached.mesh.faces.filter { it.freeSurface }.flatMap { it.vertices }
            val heights = surface.map { dot(localUp, it) * upLength }
            val deviation = if (heights.isEmpty()) 0.0 else heights.max() - heights.min()
            observations[blockEntity] = Draw(cached.mesh, bounds, fraction, localUp, deviation,
                cached.buildCount, reused, (observations[blockEntity]?.renderCallCount ?: 0L) + 1L)
        }
        return true
    }

    private fun sameNormal(first: Point, second: Point): Boolean =
        abs(first.x - second.x) <= NORMAL_TOLERANCE && abs(first.y - second.y) <= NORMAL_TOLERANCE &&
            abs(first.z - second.z) <= NORMAL_TOLERANCE

    /** Split once into block-sized texture tiles, including the oblique window-wall polygons. */
    private fun textureQuads(mesh: Mesh, lighterThanAir: Boolean): List<Quad> = buildList {
        for (face in mesh.faces) {
            // Native Create omits the solid tank floor (ceiling for lighter-than-air fluids).
            if (!face.freeSurface && if (lighterThanAir) face.normal.y > 0.5 else face.normal.y < -0.5) continue
            val (uAxis, vAxis) = textureAxes(face)
            val vertices = face.vertices.map { TextureVertex(it, dot(uAxis, it), dot(vAxis, it)) }
            val minU = floor(vertices.minOf { it.u }).toInt()
            val maxU = ceil(vertices.maxOf { it.u }).toInt()
            val minV = floor(vertices.minOf { it.v }).toInt()
            val maxV = ceil(vertices.maxOf { it.v }).toInt()
            for (u in minU until maxU) for (v in minV until maxV) {
                var tile = clipTexture(vertices, u.toDouble(), true, false)
                tile = clipTexture(tile, u + 1.0, true, true)
                tile = clipTexture(tile, v.toDouble(), false, false)
                tile = clipTexture(tile, v + 1.0, false, true)
                if (tile.size < 3) continue
                val mapped = tile.map { TextureVertex(it.point,
                    (it.u - u).coerceIn(0.0, 1.0), (it.v - v).coerceIn(0.0, 1.0)) }
                if (mapped.size == 4) {
                    add(Quad(mapped, face.normal))
                } else {
                    for (i in 1 until mapped.lastIndex) {
                        // Catnip's translucent fluid builder consumes QUADS. The repeated vertex
                        // makes its second triangle degenerate without changing the render type.
                        add(Quad(listOf(mapped[0], mapped[i], mapped[i + 1], mapped[i + 1]), face.normal))
                    }
                }
            }
        }
    }

    private fun textureAxes(face: Face): Pair<Point, Point> {
        val normal = face.normal
        if (face.freeSurface) {
            val reference = if (abs(normal.x) < 0.9) Point(1.0, 0.0, 0.0) else Point(0.0, 0.0, 1.0)
            val projection = dot(reference, normal)
            val tangent = normalize(Point(reference.x - normal.x * projection,
                reference.y - normal.y * projection, reference.z - normal.z * projection))
            return tangent to cross(tangent, normal)
        }
        return when {
            abs(normal.x) > 0.5 -> Point(0.0, 0.0, -normal.x) to Point(0.0, -1.0, 0.0)
            abs(normal.z) > 0.5 -> Point(normal.z, 0.0, 0.0) to Point(0.0, -1.0, 0.0)
            else -> Point(1.0, 0.0, 0.0) to Point(0.0, 0.0, normal.y)
        }
    }

    private fun clipTexture(
        polygon: List<TextureVertex>,
        boundary: Double,
        uAxis: Boolean,
        keepBelow: Boolean
    ): List<TextureVertex> {
        if (polygon.isEmpty()) return polygon
        val output = ArrayList<TextureVertex>(polygon.size + 1)
        var previous = polygon.last()
        var previousDistance = (if (uAxis) previous.u else previous.v) - boundary
        var previousInside = if (keepBelow) previousDistance <= CLIP_TOLERANCE else previousDistance >= -CLIP_TOLERANCE
        for (current in polygon) {
            val distance = (if (uAxis) current.u else current.v) - boundary
            val inside = if (keepBelow) distance <= CLIP_TOLERANCE else distance >= -CLIP_TOLERANCE
            if (inside != previousInside) {
                val t = (previousDistance / (previousDistance - distance)).coerceIn(0.0, 1.0)
                appendDistinct(output, interpolate(previous, current, t))
            }
            if (inside) appendDistinct(output, current)
            previous = current
            previousDistance = distance
            previousInside = inside
        }
        if (output.size > 1 && samePoint(output.first().point, output.last().point)) output.removeAt(output.lastIndex)
        return output
    }

    private fun appendDistinct(vertices: MutableList<TextureVertex>, vertex: TextureVertex) {
        if (vertices.isEmpty() || !samePoint(vertices.last().point, vertex.point)) vertices.add(vertex)
    }

    private fun samePoint(first: Point, second: Point): Boolean =
        abs(first.x - second.x) <= CLIP_TOLERANCE && abs(first.y - second.y) <= CLIP_TOLERANCE &&
            abs(first.z - second.z) <= CLIP_TOLERANCE

    private fun interpolate(first: TextureVertex, second: TextureVertex, t: Double): TextureVertex = TextureVertex(
        Point(first.point.x + (second.point.x - first.point.x) * t,
            first.point.y + (second.point.y - first.point.y) * t,
            first.point.z + (second.point.z - first.point.z) * t),
        first.u + (second.u - first.u) * t, first.v + (second.v - first.v) * t)

    private fun draw(
        quads: List<Quad>,
        sprite: TextureAtlasSprite,
        color: Int,
        light: Int,
        consumer: VertexConsumer,
        pose: PoseStack.Pose
    ) {
        val shrink = sprite.uvShrinkRatio() * 0.25f
        val centerU = (sprite.u0 + sprite.u1) * 0.5f
        val centerV = (sprite.v0 + sprite.v1) * 0.5f
        val alpha = (color ushr 24) and 255
        val red = (color ushr 16) and 255
        val green = (color ushr 8) and 255
        val blue = color and 255
        var quadIndex = 0
        while (quadIndex < quads.size) {
            val quad = quads[quadIndex++]
            var vertexIndex = 0
            while (vertexIndex < quad.vertices.size) {
                val vertex = quad.vertices[vertexIndex++]
                val textureU = sprite.getU(vertex.u.toFloat())
                val textureV = sprite.getV(vertex.v.toFloat())
                consumer.addVertex(pose.pose(), vertex.point.x.toFloat(), vertex.point.y.toFloat(), vertex.point.z.toFloat())
                    .setColor(red, green, blue, alpha)
                    .setUv(textureU + (centerU - textureU) * shrink, textureV + (centerV - textureV) * shrink)
                    .setLight(light)
                    .setNormal(pose, quad.normal.x.toFloat(), quad.normal.y.toFloat(), quad.normal.z.toFloat())
            }
        }
    }

    private fun dot(first: Point, second: Point): Double = first.x * second.x + first.y * second.y + first.z * second.z
    private fun cross(first: Point, second: Point): Point = Point(
        first.y * second.z - first.z * second.y,
        first.z * second.x - first.x * second.z,
        first.x * second.y - first.y * second.x)
    private fun normalize(point: Point): Point {
        val length = sqrt(dot(point, point))
        return Point(point.x / length, point.y / length, point.z / length)
    }
}
