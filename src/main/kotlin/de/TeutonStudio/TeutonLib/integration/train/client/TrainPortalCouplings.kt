package de.TeutonStudio.TeutonLib.integration.train.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.AllPartialModels
import com.simibubi.create.CreateClient
import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.CarriageBogey
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import createtrainfunctionality.ClientLinks
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalCouplingGeometry.Plane
import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalCouplingGeometry.Segment
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.sqrt
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import qouteall.imm_ptl.core.compat.IPPortalLookup
import qouteall.imm_ptl.core.compat.create_integration.IPCreateTrainSides
import qouteall.imm_ptl.core.compat.mixin_create.IPCarriageAccessor
import qouteall.imm_ptl.core.compat.sable_straddle.IPPortalIsometry
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPPlaneClippingVertexConsumer
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleClientSessions
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleRender
import qouteall.imm_ptl.core.portal.Mirror
import qouteall.imm_ptl.core.render.context_management.PortalRendering

/** Render each physical coupling in a common portal view, with IP's native polygon clipping. */
object TrainPortalCouplings {
    private data class End(val point: Vec3, val level: Level, val ship: ClientSubLevel?)
    private data class View(val point: Vec3, val dimension: ResourceKey<Level>, val planes: List<Plane>)
    private val replaced = Collections.newSetFromMap(IdentityHashMap<Carriage, Boolean>())
    private val debugEnabled = java.lang.Boolean.getBoolean("teutonlib.trainPortalClientTests") ||
        java.lang.Boolean.getBoolean("teutonlib.trainPortalCouplingDebug")
    private var debugState: Map<String, Any?> = emptyMap()

    /** Optional verification snapshot; production does not collect per-pair telemetry. */
    @JvmStatic fun debugState(): Map<String, Any?> = debugState

    @JvmStatic fun manages(carriage: Carriage): Boolean = carriage in replaced

    @JvmStatic fun endRender() = replaced.clear()

    @JvmStatic fun render(pose: PoseStack, buffers: MultiBufferSource, camera: Vec3) {
        replaced.clear()
        val level = Minecraft.getInstance().level ?: return
        val partial = AnimationTickHolder.getPartialTicks()
        val debugPairs = if (debugEnabled) mutableListOf<Map<String, Any?>>() else null
        if (debugPairs != null) debugState = mapOf("dimension" to level.dimension().location().toString(),
            "trainCount" to CreateClient.RAILWAYS.trains.size, "pairs" to debugPairs)
        for (train in CreateClient.RAILWAYS.trains.values) {
            val carriages = train.carriages
            for (index in 0 until carriages.size - 1) {
                val first = carriages[index]
                val second = carriages[index + 1]
                val firstLinked = linked(first)
                val secondLinked = linked(second)
                val debugPair = if (debugPairs != null) linkedMapOf<String, Any?>("train" to train.id.toString(),
                    "index" to index, "first" to debugCarriage(first), "second" to debugCarriage(second)).also {
                    debugPairs.add(it)
                } else null
                if (firstLinked == null && secondLinked == null) continue
                // Create's loop calls getDimensional() twice on the first carriage of this pair.
                replaced.add(first)
                val left = endpoint(first, false, firstLinked, level, partial)
                val right = endpoint(second, true, secondLinked, level, partial)
                debugPair?.put("left", left?.let(::debugEnd))
                debugPair?.put("right", right?.let(::debugEnd))
                if (left == null || right == null) continue
                val spacing = train.carriageSpacing.getOrNull(index)?.toDouble() ?: continue
                // Connector offsets are already included by updateCouplingAnchor(). A
                // small allowance around the train's own spacing handles custom bogeys
                // without reaching into CarriageBogey's package-private type field.
                val maximum = spacing.coerceAtLeast(1.0) + 8.0
                val rendered = mutableListOf<Segment>()
                val leftViews = views(left)
                val rightViews = views(right)
                val nativePlausible = TrainPortalCouplingGeometry.plausible(left.point, right.point, maximum)
                // Same-dimension portals can have nearby endpoints. Only the actual
                // rail path can distinguish that gap from an ordinary short coupling.
                val railGap = left.level === right.level && nativePlausible &&
                    IPCreateTrainSides.gapCrosses(train, index + 1)
                debugPair?.put("maximumLength", maximum)
                debugPair?.put("railGap", railGap)
                debugPair?.put("leftViews", leftViews.map(::debugView))
                debugPair?.put("rightViews", rightViews.map(::debugView))
                if (!railGap) {
                    for (a in leftViews) for (b in rightViews) {
                        if (a.dimension != level.dimension() || b.dimension != a.dimension) continue
                        if (!TrainPortalCouplingGeometry.plausible(a.point, b.point, maximum)) continue
                        draw(pose, buffers, camera, level, a.point, b.point, a.planes + b.planes, rendered)
                    }
                }
                // A portal may lie entirely between the wagons. Neither body then owns a
                // straddle session, so use the same portal isometry for the coupling gap.
                if (railGap || left.level !== right.level || !nativePlausible) {
                    gapViews(left, right, maximum) { a, b, dimension, plane ->
                        if (dimension == level.dimension()) draw(pose, buffers, camera, level, a, b, listOf(plane), rendered)
                    }
                    gapViews(right, left, maximum) { a, b, dimension, plane ->
                        if (dimension == level.dimension()) draw(pose, buffers, camera, level, b, a, listOf(plane), rendered)
                    }
                }
                debugPair?.put("rendered", rendered.map { mapOf("start" to debugPoint(it.start),
                    "end" to debugPoint(it.end), "firstHead" to it.firstHead, "secondHead" to it.secondHead) })
            }
        }
    }

    private fun debugPoint(point: Vec3) = listOf(point.x, point.y, point.z)
    private fun debugEnd(end: End) = mapOf("point" to debugPoint(end.point),
        "dimension" to end.level.dimension().location().toString(), "ship" to end.ship?.uniqueId?.toString())
    private fun debugView(view: View) = mapOf("point" to debugPoint(view.point),
        "dimension" to view.dimension.location().toString(), "planes" to view.planes.map {
            mapOf("point" to debugPoint(it.point), "normal" to debugPoint(it.normal))
        })
    private fun debugCarriage(carriage: Carriage) = (carriage as IPCarriageAccessor).ip_getEntities().map { (key, record) ->
        val entity = record.entity.get()
        mapOf("key" to key.location().toString(), "entity" to entity?.id,
            "dimension" to entity?.level()?.dimension()?.location()?.toString(), "removed" to entity?.isRemoved,
            "valid" to entity?.validForRender, "firstPosition" to entity?.firstPositionUpdate,
            "bound" to (entity?.carriage != null), "plot" to entity?.id?.let(ClientLinks::plotOriginOf)?.toShortString())
    }

    private fun linked(carriage: Carriage): CarriageContraptionEntity? =
        (carriage as IPCarriageAccessor).ip_getEntities().values.asSequence().mapNotNull { it.entity.get() }
            .firstOrNull { !it.isRemoved && ClientLinks.plotOriginOf(it.id) != null }

    private fun endpoint(carriage: Carriage, leading: Boolean, linked: CarriageContraptionEntity?,
        level: Level, partial: Float): End? {
        val entity = linked ?: run {
            val copies = (carriage as IPCarriageAccessor).ip_getEntities().values.mapNotNull { it.entity.get() }
            // A mixed Create/CTF coupling still has a physical far-side endpoint
            // after the ordinary wagon's last local portal copy has disappeared.
            TrainPortalCouplingGeometry.preferredEndpoint(copies,
                { !it.isRemoved && !it.firstPositionUpdate && it.validForRender }, { it.level() === level })
        } ?: return null
        if (entity.firstPositionUpdate || !entity.validForRender) return null
        val bogey = if (leading) carriage.leadingBogey() else carriage.trailingBogey()
        val world = nativeAnchor(entity, bogey, leading, partial)
        val origin = ClientLinks.plotOriginOf(entity.id) ?: return End(world, entity.level(), null)
        val ship = SubLevelContainer.getContainer(entity.level())?.getPlot(net.minecraft.world.level.ChunkPos(origin))
            ?.subLevel as? ClientSubLevel ?: return null
        if (ship.isRemoved) return null
        // CTF's target pose expresses Create's anchor in the plot without reading or
        // modifying the shared couplingAnchors last written by another portal copy.
        val target = SubLevelLinks.drawnTargetPose(entity, ship.logicalPose().rotationPoint(), origin, partial)
        val local = target.transformPositionInverse(world)
        return End(IPStraddleRender.realRenderPose(ship).transformPosition(local), entity.level(), ship)
    }

    private fun nativeAnchor(entity: CarriageContraptionEntity, bogey: CarriageBogey, leading: Boolean,
        partial: Float): Vec3 {
        // updateCouplingAnchor is Create's public geometry method and includes custom
        // bogey connector offsets. Restore its render cache immediately after sampling.
        val old = bogey.couplingAnchors.get(leading)
        return try {
            bogey.updateCouplingAnchor(entity.getPosition(partial), entity.getViewXRot(partial),
                entity.getViewYRot(partial), entity.carriage.bogeySpacing, partial, leading)
            bogey.couplingAnchors.get(leading)
        } finally {
            bogey.couplingAnchors.set(leading, old)
        }
    }

    private fun views(end: End): List<View> {
        val ship = end.ship ?: return listOf(View(end.point, end.level.dimension(), emptyList()))
        val sessions = IPStraddleClientSessions.renderSessionsOf(ship)
        val sourcePlanes = IPStraddleRender.planeFor(ship)?.let { listOf(Plane(it.point(), it.normal())) }.orEmpty()
        val result = mutableListOf(View(end.point, end.level.dimension(), sourcePlanes))
        for (session in sessions) result.add(View(session.isometry().mapPoint(end.point), session.destDim(),
            listOf(Plane(session.destOrigin(), session.destNormal()))))
        return result
    }

    private fun gapViews(left: End, right: End, maximum: Double,
        draw: (Vec3, Vec3, ResourceKey<Level>, Plane) -> Unit) {
        val box = AABB(left.point, left.point).inflate(maximum)
        for (portal in IPPortalLookup.touching(left.level, box) {
            !it.isRemoved && it !is Mirror && it.teleportable && abs(it.scale - 1.0) < 1e-6 &&
                it.destDim == right.level.dimension()
        }) {
            val isometry = IPPortalIsometry.of(portal)
            val mappedRight = isometry.unmapPoint(right.point)
            if (!TrainPortalCouplingGeometry.plausible(left.point, mappedRight, maximum) ||
                !TrainPortalCouplingGeometry.crossesOpening(left.point, mappedRight, portal.originPos, portal.normal,
                    portal.axisW, portal.axisH, portal.width * 0.5, portal.height * 0.5)) continue
            val keep = portal.normal.scale(if (left.point.subtract(portal.originPos).dot(portal.normal) >= 0) 1.0 else -1.0)
            draw(left.point, mappedRight, left.level.dimension(), Plane(portal.originPos, keep))
            draw(isometry.mapPoint(left.point), right.point, right.level.dimension(),
                Plane(isometry.mapPoint(portal.originPos), isometry.mapVec(keep).scale(-1.0)))
        }
    }

    private fun draw(pose: PoseStack, buffers: MultiBufferSource, camera: Vec3, level: Level,
        first: Vec3, second: Vec3, planes: List<Plane>, rendered: MutableList<Segment>) {
        val cuts = planes.toMutableList()
        if (PortalRendering.isRendering()) PortalRendering.getActiveClippingPlane()?.let { cuts.add(Plane(it.pos(), it.normal())) }
        val segment = TrainPortalCouplingGeometry.clip(first, second, cuts) ?: return
        if (segment.start.distanceToSqr(camera) > 64.0 * 64.0 && segment.end.distanceToSqr(camera) > 64.0 * 64.0) return
        if (rendered.any { it.start.distanceToSqr(segment.start) < 1e-6 && it.end.distanceToSqr(segment.end) < 1e-6 }) return
        rendered.add(segment)
        val wrappers = mutableListOf<IPPlaneClippingVertexConsumer>()
        var consumer = buffers.getBuffer(RenderType.solid())
        for (plane in cuts) {
            val n = pose.last().pose().transformDirection(plane.normal.toVector3f()).normalize()
            val p = pose.last().pose().transformPosition(plane.point.subtract(camera).toVector3f())
            val clipper = IPPlaneClippingVertexConsumer(consumer, n, p)
            wrappers.add(clipper)
            consumer = clipper
        }
        try {
            drawSegment(pose, consumer, camera, segment.start, segment.end,
                LevelRenderer.getLightColor(level, BlockPos.containing(segment.start)),
                LevelRenderer.getLightColor(level, BlockPos.containing(segment.end)),
                segment.firstHead, segment.secondHead)
        } finally {
            wrappers.asReversed().forEach { it.flush() }
        }
    }

    /** The endpoints supplied here are the visible, clipped world-space segment. */
    @JvmStatic fun drawSegment(pose: PoseStack, consumer: VertexConsumer, camera: Vec3, first: Vec3, second: Vec3,
        firstLight: Int, secondLight: Int, firstHead: Boolean, secondHead: Boolean) {
        val delta = second.subtract(first)
        val length = delta.length()
        if (length < 1e-5) return
        val yaw = Math.toDegrees(atan2(delta.z, delta.x)).toFloat() + 90f
        val pitch = Math.toDegrees(atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z))).toFloat()
        val air = Blocks.AIR.defaultBlockState()
        pose.pushPose()
        try {
            pose.translate(first.x - camera.x, first.y - camera.y, first.z - camera.z)
            if (firstHead) CachedBuffers.partial(AllPartialModels.TRAIN_COUPLING_HEAD, air)
                .rotateYDegrees(-yaw).rotateXDegrees(pitch).light<SuperByteBuffer>(firstLight).renderInto(pose, consumer)
            val startMargin = if (firstHead) 0.1875 else 0.0
            val endMargin = if (secondHead) 0.1875 else 0.0
            val cableLength = (length - startMargin - endMargin).coerceAtLeast(0.0)
            val count = ceil(cableLength * 4.0).toInt().coerceIn(1, 512)
            val stretch = (cableLength * 4.0 / count).toFloat()
            repeat(count) { index ->
                CachedBuffers.partial(AllPartialModels.TRAIN_COUPLING_CABLE, air)
                    .rotateYDegrees(-yaw + 180f).rotateXDegrees(-pitch)
                    .translate(0.0, 0.0, startMargin + 0.125).scale(1f, 1f, stretch)
                    .translate(0f, 0f, index / 4f).light<SuperByteBuffer>(firstLight).renderInto(pose, consumer)
            }
        } finally {
            pose.popPose()
        }
        if (secondHead) {
            pose.pushPose()
            try {
                pose.translate(second.x - camera.x, second.y - camera.y, second.z - camera.z)
                CachedBuffers.partial(AllPartialModels.TRAIN_COUPLING_HEAD, air).rotateYDegrees(-yaw + 180f)
                    .rotateXDegrees(-pitch).light<SuperByteBuffer>(secondLight).renderInto(pose, consumer)
            } finally {
                pose.popPose()
            }
        }
    }
}
