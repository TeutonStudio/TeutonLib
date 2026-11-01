package de.TeutonStudio.TeutonLib.client.hose

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.AllPartialModels
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.sable.SubLevelRenderPose
import de.TeutonStudio.TeutonLib.integration.hose.HoseEntities
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadPolicy
import de.TeutonStudio.TeutonLib.integration.hose.HosePulleyHost
import de.TeutonStudio.TeutonLib.integration.HosePulleyCompatibility
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.ceil

/** Reuses Create's hose magnet so resource packs also apply to the physical hose head. */
class HoseHeadRenderer(context: EntityRendererProvider.Context) : EntityRenderer<HoseHeadEntity>(context) {
    override fun render(
        entity: HoseHeadEntity,
        yaw: Float,
        partialTick: Float,
        pose: PoseStack,
        buffers: MultiBufferSource,
        light: Int
    ) {
        if (!physicalHeadEnabled(entity)) return
        if (renderDocked(entity, partialTick, pose, buffers, light)) {
            super.render(entity, yaw, partialTick, pose, buffers, light)
            return
        }
        pose.pushPose()
        try {
            val pulley = BuiltInRegistries.BLOCK.get(HOSE_PULLEY)
            val state = pulley.defaultBlockState()
            // Resolve the moving pulley in render space as well as simulation space so
            // the top of the hose follows interpolated Sable movement without jitter.
            val owner = entity.ownerPosition
            val ownerBlockEntity = entity.level().getBlockEntity(owner)
            val anchor = if (ownerBlockEntity != null) {
                SubLevelRenderPose.apply(
                    PoseStack(), ownerBlockEntity,
                    owner.x + 0.5, owner.y.toDouble(), owner.z + 0.5,
                    Vec3.ZERO, partialTick
                ).worldPosition
            } else {
                entity.worldAnchor()
            }
            val relativeAnchor = anchor.subtract(entity.getPosition(partialTick))
            val distance = relativeAnchor.length()
            if (distance > 1.0e-4 && distance.isFinite()) {
                val direction = relativeAnchor.scale(1.0 / distance)
                val rotation = Quaternionf().rotationTo(
                    Vector3f(0f, 1f, 0f),
                    Vector3f(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
                )
                // Orient the magnet's built-in hose and every extension along
                // the same axis, including a short or fully retracted tether.
                pose.mulPose(rotation)
                pose.pushPose()
                try {
                    pose.scale(1f, minOf(1.0, distance).toFloat(), 1f)
                    CachedBuffers.partial(AllPartialModels.HOSE_MAGNET, state)
                        .translate(-0.5, 0.0, -0.5)
                        .light<SuperByteBuffer>(light)
                        .renderInto(pose, buffers.getBuffer(RenderType.solid()))
                } finally {
                    pose.popPose()
                }

                // The magnet already covers the first block. Repeat the hose
                // texture at roughly one-block intervals beyond that point.
                val remaining = distance - 1.0
                if (remaining > 1.0e-4) {
                    val segments = ceil(remaining).toInt().coerceIn(1, MAX_SEGMENTS)
                    val segmentLength = remaining / segments
                    repeat(segments) { index ->
                        pose.pushPose()
                        try {
                            pose.translate(0.0, 1.0 + index * segmentLength, 0.0)
                            pose.scale(1f, segmentLength.toFloat(), 1f)
                            CachedBuffers.partial(AllPartialModels.HOSE, state)
                                .translate(-0.5, 0.0, -0.5)
                                .light<SuperByteBuffer>(light)
                                .renderInto(pose, buffers.getBuffer(RenderType.solid()))
                        } finally {
                            pose.popPose()
                        }
                    }
                }
            }
        } finally {
            pose.popPose()
        }
        super.render(entity, yaw, partialTick, pose, buffers, light)
    }

    private fun renderDocked(entity: HoseHeadEntity, partialTick: Float, pose: PoseStack,
        buffers: MultiBufferSource, light: Int): Boolean {
        val socketPos = entity.dockPosition ?: return false
        val socket = de.TeutonStudio.TeutonLib.integration.hose.HoseDrainConnection.get(
            entity.level().getBlockEntity(socketPos)) ?: return false
        if (socket.identity != entity.dockId || socket.headId != entity.uuid) return false
        val camera = entity.getPosition(partialTick)
        val local = socket.socketLocal()
        val state = BuiltInRegistries.BLOCK.get(HOSE_PULLEY).defaultBlockState()
        pose.pushPose()
        try {
            SubLevelRenderPose.apply(pose, socket.drain, local.x, local.y, local.z, camera, partialTick)
            CachedBuffers.partial(AllPartialModels.HOSE_MAGNET, state).translate(-.5, 0.0, -.5)
                .light<SuperByteBuffer>(light).renderInto(pose, buffers.getBuffer(RenderType.solid()))
        } finally { pose.popPose() }
        // The seated magnet follows the socket's orientation; the rope can leave at any angle.
        val ropePose = PoseStack()
        val start = SubLevelRenderPose.apply(ropePose, socket.drain, local.x, local.y + 1.0, local.z, Vec3.ZERO, partialTick).worldPosition
        val owner = entity.level().getBlockEntity(entity.ownerPosition)
        val anchor = if (owner != null) SubLevelRenderPose.apply(PoseStack(), owner,
            entity.ownerPosition.x + .5, entity.ownerPosition.y.toDouble(), entity.ownerPosition.z + .5,
            Vec3.ZERO, partialTick).worldPosition else entity.worldAnchor()
        val radial = anchor.subtract(start)
        val distance = radial.length()
        if (distance > 1e-4 && distance.isFinite()) {
            val direction = radial.scale(1.0 / distance)
            pose.pushPose()
            try {
                val relative = start.subtract(camera)
                pose.translate(relative.x, relative.y, relative.z)
                pose.mulPose(Quaternionf().rotationTo(Vector3f(0f, 1f, 0f),
                    Vector3f(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())))
                val segments = ceil(distance).toInt().coerceIn(1, MAX_SEGMENTS)
                val length = distance / segments
                repeat(segments) { index ->
                    pose.pushPose()
                    try {
                        pose.translate(0.0, index * length, 0.0)
                        pose.scale(1f, length.toFloat(), 1f)
                        CachedBuffers.partial(AllPartialModels.HOSE, state).translate(-.5, 0.0, -.5)
                            .light<SuperByteBuffer>(light).renderInto(pose, buffers.getBuffer(RenderType.solid()))
                    } finally { pose.popPose() }
                }
            } finally { pose.popPose() }
        }
        return true
    }

    override fun getTextureLocation(entity: HoseHeadEntity): ResourceLocation = TextureAtlas.LOCATION_BLOCKS

    override fun shouldRender(entity: HoseHeadEntity, frustum: Frustum, x: Double, y: Double, z: Double): Boolean =
        physicalHeadEnabled(entity) && (super.shouldRender(entity, frustum, x, y, z) ||
            frustum.isVisible(AABB(entity.position(), entity.worldAnchor()).inflate(0.5)))

    private fun physicalHeadEnabled(entity: HoseHeadEntity): Boolean {
        val level = entity.level()
        if (!level.hasChunkAt(entity.ownerPosition)) return true
        val owner = level.getBlockEntity(entity.ownerPosition)
        return owner !is HosePulleyHost || HoseHeadPolicy.enabled(owner)
    }

    companion object {
        private val HOSE_PULLEY = ResourceLocation.fromNamespaceAndPath("create", "hose_pulley")
        private const val MAX_SEGMENTS = 512
    }
}

/** Keep optional Create classes out of the subscriber's event method signature. */
@EventBusSubscriber(modid = TeutonLib.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object HoseHeadRendererRegistration {
    @SubscribeEvent
    fun register(event: EntityRenderersEvent.RegisterRenderers) {
        if (HosePulleyCompatibility.active()) {
            event.registerEntityRenderer(HoseEntities.HOSE_HEAD.get(), ::HoseHeadRenderer)
        }
    }
}
