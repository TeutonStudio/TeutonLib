package de.TeutonStudio.TeutonLib.integration.train

import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** Constant-cost overlap of a posed build's box with the finite portal opening. */
object TrainPortalClip {
    private const val SKIN_SQUARED = 0.02 * 0.02

    /**
     * [halfX], [halfY] and [halfZ] are the orthogonal half-extent vectors in world space.
     * The portal axes and normal are unit vectors. The separating-axis test also handles
     * zero-width build dimensions and avoids constructing corners or reading plot blocks.
     */
    fun touches(center: Vec3, halfX: Vec3, halfY: Vec3, halfZ: Vec3, origin: Vec3,
        normal: Vec3, axisW: Vec3, axisH: Vec3, halfWidth: Double, halfHeight: Double): Boolean {
        if (halfWidth < 0.0 || halfHeight < 0.0) return false
        val dx = center.x - origin.x
        val dy = center.y - origin.y
        val dz = center.z - origin.z

        fun separates(x: Double, y: Double, z: Double): Boolean {
            val radius = abs(x * halfX.x + y * halfX.y + z * halfX.z) +
                abs(x * halfY.x + y * halfY.y + z * halfY.z) +
                abs(x * halfZ.x + y * halfZ.y + z * halfZ.z) +
                halfWidth * abs(x * axisW.x + y * axisW.y + z * axisW.z) +
                halfHeight * abs(x * axisH.x + y * axisH.y + z * axisH.z)
            val gap = abs(x * dx + y * dy + z * dz) - radius
            // Compare squared distances to keep the skin independent of axis length.
            // A zero axis cannot separate either shape, including degenerate boxes.
            return gap > 0.0 && gap * gap > SKIN_SQUARED * (x * x + y * y + z * z)
        }

        fun crossSeparates(a: Vec3, b: Vec3): Boolean = separates(
            a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

        // Portal face/edge normals, build face normals and all build/portal edge pairs.
        // The two in-plane portal axes also cover a point-sized build directly.
        return !separates(normal.x, normal.y, normal.z) &&
            !separates(axisW.x, axisW.y, axisW.z) &&
            !separates(axisH.x, axisH.y, axisH.z) &&
            !crossSeparates(halfX, halfY) && !crossSeparates(halfY, halfZ) && !crossSeparates(halfZ, halfX) &&
            !crossSeparates(halfX, axisW) && !crossSeparates(halfX, axisH) &&
            !crossSeparates(halfY, axisW) && !crossSeparates(halfY, axisH) &&
            !crossSeparates(halfZ, axisW) && !crossSeparates(halfZ, axisH)
    }
}
