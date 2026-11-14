package de.TeutonStudio.TeutonLib.integration.sable

import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry
import de.TeutonStudio.TeutonLib.integration.hose.*
import de.TeutonStudio.TeutonLib.network.HoseCablePayload
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.physics.`object`.rope.RopeHandle
import dev.ryanhcode.sable.api.physics.`object`.rope.RopePhysicsObject
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.MoverType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.abs

/** One native, colliding rope per head. Endpoints are kinematic world anchors; only this adapter
 * applies tensile endpoint impulses, so native attachments cannot double those forces. */
object HoseCablePhysics {
    private class ManagedRope(seed: Collection<Vector3d>) : RopePhysicsObject(seed, HoseCableMath.RADIUS) {
        override fun onRemoved() { if (isActive) super.onRemoved() }
    }
    private class Cable(val head: HoseHeadEntity) {
        var rope: RopePhysicsObject? = null
        var system: SubLevelPhysicsSystem? = null
        var points = head.savedCablePoints?.takeIf(HoseCableMath::valid) ?: listOf(head.cableAnchor(), head.cableEnd())
        var restLength = head.cableLength()
        var nativeLength = restLength
        var contactAllowance = 0.0
        var reseed = false
        var hasPose = head.savedCablePoints?.let(HoseCableMath::valid) == true
        var paused = false
        var lastSnapshot: HoseCablePayload? = null
        var startAttachment: Vec3? = null
        var endAttachment: Vec3? = null
        val watchers = mutableSetOf<UUID>()
    }
    private val cables = mutableMapOf<UUID, Cable>()

    fun ensure(head: HoseHeadEntity) {
        if (head.level() is ServerLevel) {
            val existing = cables[head.uuid]
            if (existing != null && existing.head !== head) remove(existing.head)
            cables.getOrPut(head.uuid) { Cable(head) }
        }
    }
    fun ready(head: HoseHeadEntity): Boolean = cables[head.uuid]?.let { !it.paused } ?: true
    fun points(head: HoseHeadEntity): List<Vec3> = cables[head.uuid]?.points ?: head.savedCablePoints.orEmpty()
    fun constrain(head: HoseHeadEntity, target: Vec3): Vec3 {
        val cable = cables[head.uuid]
        val path = (cable?.points?.takeIf { it.size > 2 } ?: listOf(head.cableAnchor(), head.cableEnd())).toMutableList()
        path[0] = head.cableAnchor(); path[path.lastIndex] = target
        val taut = HoseCableRouting.taut(head,path)
        return HoseCableMath.limitEnd(taut, target, head.cableLength())
    }
    fun remove(head: HoseHeadEntity) {
        val cable = cables[head.uuid]?.takeIf { it.head === head } ?: return
        head.savedCablePoints = cable.points.toList()
        suspend(cable)
        retire(cable)
        cables.remove(head.uuid)
    }
    fun transform(head: HoseHeadEntity, transform: (Vec3) -> Vec3) {
        val saved = points(head).map(transform)
        remove(head)
        head.savedCablePoints = saved.takeIf(HoseCableMath::valid)
    }
    private fun suspend(cable: Cable) {
        cable.rope?.let { rope -> cable.system?.removeObject(rope) }
        cable.rope = null; cable.system = null
        cable.startAttachment = null; cable.endAttachment = null
    }
    private fun retire(cable: Cable) {
        val world = cable.head.level() as? ServerLevel ?: return
        val packet = HoseCablePayload(world.dimension().location(), cable.head.uuid, emptyList(), Vec3.ZERO, Vec3.ZERO, false)
        world.server.playerList.players.filter { it.uuid in cable.watchers }.forEach { PacketDistributor.sendToPlayer(it, packet) }
        cable.watchers.clear(); cable.lastSnapshot = null
    }
    private fun loaded(cable: Cable): Boolean {
        val head = cable.head
        val world = head.level() as? ServerLevel ?: return false
        if (!head.isAlive || head.validOwner() == null) return false
        if (head.dockId != null && head.connector() == null) return false
        val points = listOf(head.cableAnchor()) + cable.points + head.cableEnd()
        val minX=kotlin.math.floor((points.minOf { it.x }-1.0)/16.0).toInt()
        val maxX=kotlin.math.floor((points.maxOf { it.x }+1.0)/16.0).toInt()
        val minZ=kotlin.math.floor((points.minOf { it.z }-1.0)/16.0).toInt()
        val maxZ=kotlin.math.floor((points.maxOf { it.z }+1.0)/16.0).toInt()
        if ((maxX-minX).toLong()*(maxZ-minZ) > 4096) return false
        for (x in minX..maxX) for (z in minZ..maxZ)
            if (!dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager.isChunkLoadedEnough(world,x,z)) return false
        // Sample every traversed chunk, including stretched segments; hasChunkAt never loads it.
        return points.zipWithNext().all { (a, b) ->
            val steps = kotlin.math.ceil(a.distanceTo(b)).toInt().coerceIn(1, 1024)
            (0..steps).all { world.hasChunkAt(BlockPos.containing(a.lerp(b, it.toDouble()/steps))) }
        }
    }
    private fun create(cable: Cable, system: SubLevelPhysicsSystem) {
        val a = cable.head.cableAnchor(); val b = cable.head.cableEnd()
        val length = (cable.head.cableLength()-cable.contactAllowance).coerceAtLeast(.001)
        val count = HoseCableMath.segmentCount(length)
        val saved = cable.points.takeIf { cable.hasPose }
        val seed = HoseCableSeed.points(cable.head,saved,a,b,length,count).map { Vector3d(it.x,it.y,it.z) }
        val rope = ManagedRope(seed)
        cable.rope = rope; cable.system = system
        system.addObject(rope)
        cable.restLength = cable.head.cableLength()
        cable.nativeLength = length
        rope.setFirstSegmentLength((length - (count-1)*HoseCableMath.SEGMENT_LENGTH).coerceAtLeast(.001))
        attach(cable)
    }
    private fun attach(cable: Cable) {
        val rope = cable.rope ?: return
        val a = cable.head.cableAnchor(); val b = cable.head.cableEnd()
        if (cable.startAttachment?.distanceToSqr(a)?.let { it <= 1e-12 } != true) {
            rope.setAttachment(RopeHandle.AttachmentPoint.START, Vector3d(a.x, a.y, a.z), null)
            cable.startAttachment = a
        }
        if (cable.endAttachment?.distanceToSqr(b)?.let { it <= 1e-12 } != true) {
            rope.setAttachment(RopeHandle.AttachmentPoint.END, Vector3d(b.x, b.y, b.z), null)
            cable.endAttachment = b
        }
    }
    private fun resize(cable: Cable) {
        val rope = cable.rope ?: return
        val length = (cable.head.cableLength()-cable.contactAllowance).coerceAtLeast(.001)
        cable.restLength = cable.head.cableLength()
        // Leave the native solver alone when the paid-out length has not changed.
        if (abs(cable.nativeLength-length) <= 1e-4) return
        val existingCount = rope.points.size-1
        val firstLength = length-(existingCount-1)*HoseCableMath.SEGMENT_LENGTH
        // A small overlap at segment boundaries avoids repeatedly adding/removing a node
        // as the contact allowance fluctuates around a whole metre.
        val count = if (firstLength in .1..1.1) existingCount else HoseCableMath.segmentCount(length)
        while (rope.points.size < count+1) {
            val a = cable.head.cableAnchor()
            rope.addPoint(Vector3d(a.x, a.y, a.z))
        }
        while (rope.points.size > count+1) rope.removeFirstPoint()
        // Removing/inserting the start node also changes which native node is pinned.
        if (count != existingCount) cable.startAttachment = null
        rope.setFirstSegmentLength((length - (count-1)*HoseCableMath.SEGMENT_LENGTH).coerceAtLeast(.001))
        if (abs(cable.nativeLength-length) > 1e-6) rope.wakeUp()
        cable.nativeLength = length
    }
    @SubscribeEvent fun before(event: ForgeSablePrePhysicsTickEvent) {
        if (event.timeStep <= 0 || !event.timeStep.isFinite()) return
        for (cable in cables.values.toList()) {
            val head = cable.head
            if (head.level() !== event.physicsSystem.level) continue
            if (!loaded(cable)) {
                cable.paused = true; suspend(cable)
                head.connector()?.let { it.tension = 0.0; it.overloadSeconds = 0.0 }
                continue
            }
            cable.paused = false
            if (cable.reseed) { suspend(cable); cable.reseed = false }
            if (cable.rope?.isActive != true) create(cable, event.physicsSystem)
            resize(cable); attach(cable)
            // Sable clears queued force groups at the start of each substep. Apply endpoint
            // impulses in Pre, after that clear and before the body's queued forces are consumed.
            tension(cable,event.timeStep)
        }
    }
    @SubscribeEvent fun after(event: ForgeSablePostPhysicsTickEvent) {
        val dt = event.timeStep
        if (dt <= 0 || !dt.isFinite()) return
        for (cable in cables.values.toList()) {
            if (cable.head.level() !== event.physicsSystem.level || cable.paused) continue
            val rope = cable.rope?.takeIf { it.isActive } ?: continue
            rope.updatePose()
            val next = rope.points.map { Vec3(it.x, it.y, it.z) }
            if (!HoseCableMath.valid(next)) { cable.paused = true; suspend(cable); continue }
            val resolved = HoseCableCollisions.resolve(cable.head,next,cable.points)
            // Corner contacts spend length between native nodes. Reserve that length in the
            // variable first segment/topology, so the displayed route cannot create extra hose.
            val allowance = (HoseCableMath.length(resolved.points)-HoseCableMath.length(next))
                .coerceIn(0.0,(cable.head.cableLength()-.001).coerceAtLeast(0.0))
            // Contact edges appear and disappear between native nodes. Feeding that noise
            // straight into the winch changes topology every substep and pumps the chain.
            if (abs(allowance-cable.contactAllowance) > .005)
                cable.contactAllowance += (allowance-cable.contactAllowance)*(1.0-kotlin.math.exp(-dt/.15))
            cable.reseed = resolved.displaced
            cable.points = resolved.points
            cable.hasPose = true
        }
    }
    private fun tension(cable: Cable, dt: Double) {
        val head = cable.head
        val owner = head.validOwner() ?: return
        val socket = head.connector()
        val path = cable.points.toMutableList()
        path[0] = head.cableAnchor(); path[path.lastIndex] = head.cableEnd()
        val taut = HoseCableRouting.taut(head,path)
        val distance = HoseCableMath.length(taut)
        val aDirection = HoseCableMath.startTangent(taut)
        val bDirection = HoseCableMath.endTangent(taut)
        val bodyA = Sable.HELPER.getContaining(owner) as? ServerSubLevel
        val bodyB = socket?.let { Sable.HELPER.getContaining(it.drain) as? ServerSubLevel }
        val localA = Vec3(owner.blockPos.x+.5, owner.blockPos.y-HoseCableMath.EXIT, owner.blockPos.z+.5)
        val localB = socket?.socketLocal()?.add(0.0,5.0/16.0+HoseCableMath.DOCK_STUB,0.0)
        val world = head.level()
        val va = if (bodyA != null) Sable.HELPER.getVelocity(world, bodyA, localA) else Vec3.ZERO
        val vb = if (bodyB != null && localB != null) Sable.HELPER.getVelocity(world, bodyB, localB) else head.deltaMovement.scale(20.0)
        // Use physical attachment/contact velocities. Differencing successive polylines would
        // interpret a teleport or a topology change as a huge artificial damping velocity.
        val velocities=taut.mapIndexed { index,point ->
            when (index) {
                0 -> va
                taut.lastIndex -> vb
                else -> {
                    val radius=HoseCableMath.RADIUS*2
                    val bounds=dev.ryanhcode.sable.companion.math.BoundingBox3d().set(point.x-radius,point.y-radius,point.z-radius,
                        point.x+radius,point.y+radius,point.z+radius)
                    val contact=Sable.HELPER.getAllIntersecting(world,bounds).firstOrNull { it is ServerSubLevel } as? ServerSubLevel
                    if (contact == null) Vec3.ZERO else Sable.HELPER.getVelocity(world,contact,contact.logicalPose().transformPositionInverse(point))
                }
            }
        }
        val velocity=(0 until taut.lastIndex).sumOf { index ->
            taut[index+1].subtract(taut[index]).normalize().dot(velocities[index+1].subtract(velocities[index]))
        }
        val force = HoseTension.force(distance, cable.restLength, velocity,
            ServerConfig.hoseSpringStiffness(), ServerConfig.hoseDamping())
        if (socket != null) {
            socket.tension = force
            socket.overloadSeconds = HoseTension.overload(socket.overloadSeconds, force, ServerConfig.hoseBreakForce(), dt)
            if (socket.overloadSeconds+1e-9 >= ServerConfig.hoseBreakDelay()) { socket.release(); return }
        }
        // Do not inject artificial self impulses into a single rigid body.
        if (bodyA != null && bodyA === bodyB) return
        fun inverseMass(body: ServerSubLevel?, point: Vec3?, direction: Vec3): Double {
            if (body == null || point == null) return 0.0
            val normal = body.logicalPose().orientation().transformInverse(Vector3d(direction.x, direction.y, direction.z))
            return body.massTracker.getInverseNormalMass(Vector3d(point.x, point.y, point.z), normal)
        }
        val inverse = inverseMass(bodyA, localA, aDirection) + inverseMass(bodyB, localB, bDirection) + if (socket == null) 1.0 else 0.0
        val impulse = HoseTension.impulse(force.coerceAtMost(ServerConfig.hoseBreakForce()), dt, inverse,
            ServerConfig.hoseSpringStiffness(), ServerConfig.hoseDamping())
        fun apply(body: ServerSubLevel?, point: Vec3?, direction: Vec3) {
            if (body == null || point == null || impulse <= 0) return
            val vector = body.logicalPose().orientation().transformInverse(Vector3d(direction.x, direction.y, direction.z).mul(impulse))
            body.getOrCreateQueuedForceGroup(HoseConnectors.TENSION.get()).applyAndRecordPointForce(Vector3d(point.x, point.y, point.z), vector)
        }
        apply(bodyA, localA, aDirection); apply(bodyB, localB, bDirection)
        if (socket == null) {
            head.deltaMovement = head.deltaMovement.add(bDirection.scale(impulse / 20.0))
            val limited = HoseCableMath.limitEnd(taut, head.cableEnd(), cable.restLength)
            head.move(MoverType.SELF, limited.subtract(head.cableEnd()))
        }
    }
    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        for (cable in cables.values.toList()) {
            val head = cable.head
            if (!head.isAlive) { remove(head); continue }
            val world = head.level() as? ServerLevel ?: continue
            if (world.gameTime % 2L != 0L) continue
            sync(cable, world)
        }
    }
    private fun sync(cable: Cable, world: ServerLevel) {
        val head = cable.head
        val socket = head.connector()
        val up = if (socket != null) SubLevelGeometry.projectToWorld(world, socket.socketLocal().add(0.0, 1.0, 0.0))
            .subtract(socket.socketWorld()).normalize() else HoseCableMath.endTangent(cable.points)
        val next = HoseCablePayload(world.dimension().location(), head.uuid, cable.points.toList(), head.position(), up, socket != null, head.ownerPosition, socket?.blockPos)
        val previous = cable.lastSnapshot
        val changed = previous == null || previous.points.size != next.points.size || previous.docked != next.docked ||
            previous.headPosition.distanceToSqr(next.headPosition) > 1e-6 || previous.headUp.distanceToSqr(next.headUp) > 1e-6 ||
            previous.points.zip(next.points).any { (a,b) -> a.distanceToSqr(b) > 1e-6 }
        val bounds = cable.points.fold(AABB(head.position(), head.position())) { box, point -> box.minmax(AABB(point, point)) }.inflate(2.0)
        val viewers = world.players().filter { player ->
            // View-distance broad phase followed by per-segment distance: don't track a huge diagonal AABB.
            val range = (world.server.playerList.viewDistance*16.0).coerceAtLeast(32.0)
            bounds.inflate(range).contains(player.eyePosition) && cable.points.zipWithNext().any { (a,b) ->
                val ab = b.subtract(a); val t = if (ab.lengthSqr() > 1e-12) player.eyePosition.subtract(a).dot(ab)/ab.lengthSqr() else 0.0
                player.eyePosition.distanceToSqr(a.add(ab.scale(t.coerceIn(0.0,1.0)))) <= range*range
            }
        }
        val visible = viewers.mapTo(hashSetOf()) { it.uuid }
        world.server.playerList.players.filter { it.uuid in cable.watchers && it.uuid !in visible }.forEach {
            PacketDistributor.sendToPlayer(it, next.copy(points = emptyList()))
        }
        viewers.filter { changed || it.uuid !in cable.watchers }.forEach { PacketDistributor.sendToPlayer(it, next) }
        cable.watchers.clear(); cable.watchers.addAll(visible)
        if (changed) cable.lastSnapshot = next
    }
    @SubscribeEvent fun stopping(event: net.neoforged.neoforge.event.server.ServerStoppingEvent) {
        // ServerStopped fires after Sable has freed the native scene; never touch handles there.
        cables.values.toList().forEach { suspend(it); it.paused = true }
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    fun unload(event: net.neoforged.neoforge.event.level.LevelEvent.Unload) {
        cables.values.filter { it.head.level() === event.level }.forEach { suspend(it); it.paused = true }
    }
    @SubscribeEvent fun stopped(event: net.neoforged.neoforge.event.server.ServerStoppedEvent) { cables.clear(); HoseConnectorRegistry.clear() }
}
