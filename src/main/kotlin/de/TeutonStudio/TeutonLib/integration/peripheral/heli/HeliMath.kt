package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object HeliMath {
    const val MAX_TILT_DEGREES = 12.0
    private const val EPSILON = 1.0e-12

    fun targetIsActive(currentTick: Long, updateTick: Long): Boolean =
        currentTick >= updateTick && currentTick - updateTick < 20L

    fun normalized(vector: Vector3dc): Vector3d {
        val lengthSquared = vector.lengthSquared()
        require(lengthSquared.isFinite() && lengthSquared > EPSILON) { "Direction must not be a zero vector" }
        return Vector3d(vector).normalize()
    }

    /** Roll is around local +Z and pitch around local +X. */
    fun adjustTilt(base: Vector3dc, deltaRollDegrees: Double, deltaPitchDegrees: Double): Vector3d {
        require(deltaRollDegrees.isFinite()) { "deltaRoll must be finite" }
        require(deltaPitchDegrees.isFinite()) { "deltaPitch must be finite" }
        val rotation = Quaterniond()
            .rotateX(Math.toRadians(deltaPitchDegrees))
            .rotateZ(Math.toRadians(deltaRollDegrees))
        return normalized(rotation.transform(normalized(base)))
    }

    fun clampToCone(direction: Vector3dc, axis: Vector3dc, degrees: Double = MAX_TILT_DEGREES): Vector3d {
        val unitDirection = normalized(direction)
        val unitAxis = normalized(axis)
        val cosine = unitDirection.dot(unitAxis).coerceIn(-1.0, 1.0)
        val limit = Math.toRadians(degrees)
        if (acos(cosine) <= limit) return unitDirection

        val tangent = unitDirection.sub(Vector3d(unitAxis).mul(cosine), Vector3d())
        if (tangent.lengthSquared() <= EPSILON) tangent.set(1.0, 0.0, 0.0).cross(unitAxis)
        tangent.normalize()
        return Vector3d(unitAxis).mul(cos(limit)).add(tangent.mul(sin(limit))).normalize()
    }

    /** Minecraft yaw: south is 0, west is 90, north is 180 and east is 270 degrees. */
    fun yawDegrees(worldForward: Vector3dc): Double {
        require(worldForward.x().isFinite() && worldForward.z().isFinite()) { "Sublevel orientation is not finite" }
        require(worldForward.x() * worldForward.x() + worldForward.z() * worldForward.z() > EPSILON) {
            "Sublevel has no determinable horizontal yaw"
        }
        var yaw = Math.toDegrees(atan2(-worldForward.x(), worldForward.z()))
        if (yaw < 0.0) yaw += 360.0
        if (yaw >= 360.0) yaw -= 360.0
        return yaw
    }
}
