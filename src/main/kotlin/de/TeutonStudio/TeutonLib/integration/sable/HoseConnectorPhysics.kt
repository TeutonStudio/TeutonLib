package de.TeutonStudio.TeutonLib.integration.sable

import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.integration.hose.HoseDrainConnection
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import de.TeutonStudio.TeutonLib.integration.hose.HoseTension
import dev.ryanhcode.sable.Sable
import de.TeutonStudio.TeutonLib.integration.hose.HoseConnectors
import org.joml.Vector3d
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import java.util.Collections
import java.util.WeakHashMap
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent

/** Each connection is integrated once per Sable substep, independently of either body's actor order. */
object HoseConnectorPhysics {
    private val connectors = Collections.newSetFromMap(WeakHashMap<HoseDrainConnection, Boolean>())
    fun register(connector: HoseDrainConnection) { connectors.add(connector) }

    @SubscribeEvent
    fun stopped(event: net.neoforged.neoforge.event.server.ServerStoppedEvent) {
        connectors.clear()
        de.TeutonStudio.TeutonLib.integration.hose.HoseConnectorRegistry.clear()
    }

    @SubscribeEvent
    fun physics(event: ForgeSablePrePhysicsTickEvent) {
        val world = event.physicsSystem.level
        val dt = event.timeStep
        if (dt <= 0.0 || !dt.isFinite()) return
        for (connector in connectors.toList()) {
            if (connector.level !== world) continue
            if (!connector.active()) {
                connector.overloadSeconds = 0.0
                connector.tension = 0.0
                continue
            }
            val head = connector.head() ?: continue
            val owner = head.validOwner() ?: continue
            val bodyA = Sable.HELPER.getContaining(owner) as? ServerSubLevel
            val bodyB = Sable.HELPER.getContaining(connector.drain) as? ServerSubLevel
            val a = head.worldAnchor()
            val b = connector.socketWorld()
            val radial = b.subtract(a)
            val distance = radial.length()
            val normal = if (distance > 1e-9) radial.scale(1.0 / distance) else Vec3.ZERO
            val localA = Vec3(owner.blockPos.x + .5, owner.blockPos.y.toDouble(), owner.blockPos.z + .5)
            val velocityA = if (bodyA != null) Sable.HELPER.getVelocity(world, bodyA, localA) else Vec3.ZERO
            val velocityB = if (bodyB != null) Sable.HELPER.getVelocity(world, bodyB, connector.socketLocal()) else Vec3.ZERO
            val force = HoseTension.force(distance, head.hoseLength + HoseHeadEntity.END_ALLOWANCE.toDouble(),
                velocityB.subtract(velocityA).dot(normal), ServerConfig.hoseSpringStiffness(), ServerConfig.hoseDamping())
            connector.tension = force
            connector.overloadSeconds = HoseTension.overload(connector.overloadSeconds, force, ServerConfig.hoseBreakForce(), dt)
            if (connector.overloadSeconds + 1e-9 >= ServerConfig.hoseBreakDelay()) {
                connector.release()
                continue
            }
            // Two anchors on the same rigid body must never produce artificial self impulses.
            if (bodyA === bodyB) continue
            fun inverseMass(body: ServerSubLevel?, localPoint: Vec3): Double {
                if (body == null) return 0.0
                val localNormal = body.logicalPose().orientation().transformInverse(Vector3d(normal.x, normal.y, normal.z))
                return body.massTracker.getInverseNormalMass(Vector3d(localPoint.x, localPoint.y, localPoint.z), localNormal)
            }
            val inverseMass = inverseMass(bodyA, localA) + inverseMass(bodyB, connector.socketLocal())
            // Implicit spring/damper integration stays stable even for a one-block lightweight ship.
            val impulse = normal.scale(HoseTension.impulse(force.coerceAtMost(ServerConfig.hoseBreakForce()), dt,
                inverseMass, ServerConfig.hoseSpringStiffness(), ServerConfig.hoseDamping()))
            fun apply(body: ServerSubLevel, localPoint: Vec3, worldImpulse: Vec3) {
                val localImpulse = body.logicalPose().orientation().transformInverse(Vector3d(worldImpulse.x, worldImpulse.y, worldImpulse.z))
                body.getOrCreateQueuedForceGroup(HoseConnectors.TENSION.get()).applyAndRecordPointForce(
                    Vector3d(localPoint.x, localPoint.y, localPoint.z), localImpulse)
            }
            if (bodyA != null) apply(bodyA, localA, impulse)
            if (bodyB != null) apply(bodyB, connector.socketLocal(), impulse.scale(-1.0))
        }
    }
}
