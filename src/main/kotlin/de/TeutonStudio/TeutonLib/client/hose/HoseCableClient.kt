package de.TeutonStudio.TeutonLib.client.hose

import com.mojang.blaze3d.vertex.PoseStack
import de.TeutonStudio.TeutonLib.api.sable.SubLevelRenderPose
import de.TeutonStudio.TeutonLib.integration.hose.HoseCableMath
import de.TeutonStudio.TeutonLib.integration.hose.HoseDrainConnection
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import de.TeutonStudio.TeutonLib.network.HoseCablePayload
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID

/** World-stage rendering keeps an entire strand visible independently of entity tracking. */
object HoseCableClient {
    private class Snapshot(val previous: HoseCablePayload, val current: HoseCablePayload, val received: Double) {
        fun progress(time: Double) = ((time-received)/2.0).coerceIn(0.0,1.0)
        fun pose(time: Double): HoseCablePayload {
            val t = progress(time)
            return current.copy(points = previous.points.zip(current.points).map { (a,b) -> a.lerp(b,t) },
                headPosition = previous.headPosition.lerp(current.headPosition,t), headUp = previous.headUp.lerp(current.headUp,t))
        }
    }
    private val strands = mutableMapOf<UUID, Snapshot>()
    private val rendered = mutableSetOf<UUID>()
    fun wasRendered(id: UUID) = id in rendered
    fun points(id: UUID): List<Vec3> = strands[id]?.current?.points.orEmpty()
    fun apply(packet: HoseCablePayload) {
        if (packet.points.isEmpty()) {
            if (strands[packet.id]?.current?.dimension == packet.dimension) strands.remove(packet.id)
            return
        }
        val level = Minecraft.getInstance().level ?: return
        if (packet.dimension != level.dimension().location()) return
        val now = level.gameTime + Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false).toDouble()
        val old = strands[packet.id]?.takeIf { it.current.docked == packet.docked &&
            it.current.points.first().distanceToSqr(packet.points.first()) < 32.0*32.0 &&
            it.current.headPosition.distanceToSqr(packet.headPosition) < 32.0*32.0 }?.pose(now) ?: packet
        // Continue from the pose currently on screen, including early/late packets and changes
        // in contact topology. Starting again at the last packet would rewind every update.
        strands[packet.id] = Snapshot(old.copy(points = HoseCableMath.matchTopology(old.points,packet.points)), packet, now)
    }
    @SubscribeEvent fun logout(event: ClientPlayerNetworkEvent.LoggingOut) { strands.clear(); rendered.clear() }
    @SubscribeEvent fun unload(event: net.neoforged.neoforge.event.level.LevelEvent.Unload) {
        val level=event.level as? net.minecraft.client.multiplayer.ClientLevel ?: return
        strands.entries.removeIf { it.value.current.dimension == level.dimension().location() }
    }
    @SubscribeEvent fun render(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return
        rendered.clear()
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val partial = event.partialTick.getGameTimeDeltaPartialTick(false)
        val camera = event.camera.position
        val buffers = mc.renderBuffers().bufferSource()
        val state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:hose_pulley")).defaultBlockState()
        for (snapshot in strands.values.toList()) {
            val current = snapshot.current
            if (current.dimension != level.dimension().location()) continue
            val t = snapshot.progress(level.gameTime+partial.toDouble())
            val points = snapshot.previous.points.zip(current.points).map { (a,b) -> a.lerp(b,t) }.toMutableList()
            val owner = level.getBlockEntity(current.ownerPosition)
            if (owner != null) points[0] = SubLevelRenderPose.apply(PoseStack(), owner,
                current.ownerPosition.x+.5, current.ownerPosition.y.toDouble(), current.ownerPosition.z+.5, Vec3.ZERO, partial).worldPosition
            var up = snapshot.previous.headUp.lerp(current.headUp,t).normalize()
            val headHeight=if (current.docked) .5 else 5.0/16.0
            val socket = current.dockPosition?.let { HoseDrainConnection.get(level.getBlockEntity(it)) }
            if (current.docked && socket != null) {
                val local = socket.socketLocal()
                fun project(point: Vec3) = SubLevelRenderPose.apply(PoseStack(), socket.drain,
                    point.x, point.y, point.z, Vec3.ZERO, partial).worldPosition
                val base = project(local)
                up = project(local.add(0.0,1.0,0.0)).subtract(base).normalize()
                points[points.lastIndex] = base.add(up.scale(headHeight))
            } else if (current.docked) {
                points[points.lastIndex] = snapshot.previous.headPosition.lerp(current.headPosition,t).add(up.scale(headHeight))
            } else {
                val entity = level.entitiesForRendering().firstOrNull { it.uuid == current.id } as? HoseHeadEntity
                if (entity != null) points[points.lastIndex] = entity.getPosition(partial).add(0.0,entity.bbHeight*.5,0.0)
                val delta = points[points.lastIndex-1].subtract(points.last())
                if (delta.lengthSqr() > 1e-12) up = delta.normalize()
            }
            val bounds = points.fold(AABB(points.first(),points.first())) { box, point -> box.minmax(AABB(point,point)) }.inflate(.5)
            if (!event.frustum.isVisible(bounds)) continue
            rendered.add(current.id)
            val sprite = mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.parse("create:block/hose"))
            HoseCableMesh.draw(points,camera,sprite,buffers.getBuffer(RenderType.solid()),::light)
            val base = points.last().subtract(up.scale(headHeight))
            val pose = PoseStack()
            pose.translate(base.x-camera.x,base.y-camera.y,base.z-camera.z)
            pose.mulPose(rotation(up)); pose.translate(-.5,0.0,-.5)
            mc.blockRenderer.modelRenderer.renderModel(pose.last(),buffers.getBuffer(RenderType.solid()),state,
                mc.modelManager.getModel(if (current.docked) HoseHeadModelRegistration.SEATED else HoseHeadModelRegistration.HEAD),1f,1f,1f,light(base),OverlayTexture.NO_OVERLAY)
        }
    }
    private fun light(point: Vec3): Int {
        val level = Minecraft.getInstance().level ?: return 0
        val pos = BlockPos.containing(point)
        return if (level.hasChunkAt(pos)) LevelRenderer.getLightColor(level,pos) else 0
    }
    private fun rotation(up: Vec3): Quaternionf = if (up.lengthSqr() < 1e-12) Quaternionf() else
        Quaternionf().rotationTo(Vector3f(0f,1f,0f),Vector3f(up.x.toFloat(),up.y.toFloat(),up.z.toFloat()))
}
