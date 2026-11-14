package de.TeutonStudio.TeutonLib.client

import com.mojang.blaze3d.vertex.PoseStack
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.sable.SubLevelRenderPose
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkAntennaGeometry
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkAntennaPayloads
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkAntennaSides
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinkItem
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f

@EventBusSubscriber(modid = TeutonLib.MOD_ID, value = [Dist.CLIENT])
object LinkAntennaClient {
    val MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "block/link_antenna"))
    private data class Antenna(val id: UUID, val position: BlockPos, val side: Direction)
    private val antennas = mutableListOf<Antenna>()
    private var dimension = ""
    private var marker: CompoundTag? = null
    private var markerUntil = 0L
    private var projected: Vector4f? = null
    private var markerWorld: Vec3? = null

    fun receive(data: CompoundTag) {
        val mc = Minecraft.getInstance()
        when (data.getString("kind")) {
            "antennas" -> {
                dimension = data.getString("dimension")
                antennas.clear()
                data.getList("anchors", 10).forEach { value ->
                    val tag = value as CompoundTag
                    if (tag.hasUUID("id")) {
                        val legacy = Direction.entries.firstOrNull { it.name == tag.getString("side") } ?: Direction.UP
                        val mask = tag.getInt("sides").takeIf(LinkAntennaSides::valid) ?: LinkAntennaSides.bit(legacy)
                        LinkAntennaSides.directions(mask).forEach { side ->
                            antennas.add(Antenna(tag.getUUID("id"), BlockPos.of(tag.getLong("position")), side))
                        }
                    }
                }
            }
            "overview" -> (mc.screen as? LinkAntennaScreen)?.receive(data)
            "marker" -> {
                marker = data
                markerUntil = (mc.level?.gameTime ?: 0) + data.getInt("remaining").coerceIn(0, 300)
            }
            "clear_marker" -> { marker = null; projected = null; markerWorld = null }
        }
    }
    @SubscribeEvent fun logout(event: ClientPlayerNetworkEvent.LoggingOut) {
        antennas.clear(); marker = null; projected = null; markerWorld = null; dimension = ""
    }
    @SubscribeEvent fun click(event: InputEvent.InteractionKeyMappingTriggered) {
        if (!event.isUseItem) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (mc.screen != null || level.dimension().location().toString() != dimension ||
            player.mainHandItem.item is PeripheralLinkItem || player.offhandItem.item is PeripheralLinkItem) return
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(player.blockInteractionRange()))
        val nearest = antennas.mapNotNull { antenna ->
            if (!level.hasChunkAt(antenna.position)) null else
                LinkAntennaGeometry.hit(level, antenna.position, antenna.side, start, end)?.let { antenna to it }
        }.minByOrNull { it.second.distanceToSqr(start) } ?: return
        val obstruction = level.clip(ClipContext(start, nearest.second, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        if (LinkAntennaGeometry.occluded(obstruction, nearest.first.position, start, nearest.second)) return
        event.isCanceled = true
        event.setSwingHand(false)
        if (event.hand != InteractionHand.MAIN_HAND) return
        PacketDistributor.sendToServer(LinkAntennaPayloads.Command(CompoundTag().also {
            it.putString("action", "open"); it.putUUID("target", nearest.first.id); it.putLong("position", nearest.first.position.asLong())
        }))
    }
    @SubscribeEvent fun render(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val camera = event.camera.position
        val partial = event.partialTick.getGameTimeDeltaPartialTick(false)
        if (level.dimension().location().toString() == dimension) {
            val buffers = mc.renderBuffers().bufferSource()
            val renderType = net.minecraft.client.renderer.Sheets.cutoutBlockSheet()
            val consumer = buffers.getBuffer(renderType)
            val model = mc.modelManager.getModel(MODEL)
            antennas.forEach { antenna ->
                if (!level.hasChunkAt(antenna.position)) return@forEach
                val host = level.getBlockEntity(antenna.position) ?: return@forEach
                val pose = PoseStack()
                SubLevelRenderPose.apply(pose, host, antenna.position.x.toDouble(), antenna.position.y.toDouble(), antenna.position.z.toDouble(), camera, partial)
                pose.translate(.5, .5, .5)
                pose.mulPose(Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), Vector3f(antenna.side.stepX.toFloat(), antenna.side.stepY.toFloat(), antenna.side.stepZ.toFloat())))
                pose.translate(-.5, -.5, -.5)
                mc.blockRenderer.modelRenderer.renderModel(pose.last(), consumer, null, model, 1f, 1f, 1f,
                    net.minecraft.client.renderer.LevelRenderer.getLightColor(level, antenna.position.relative(antenna.side)), OverlayTexture.NO_OVERLAY)
            }
            buffers.endBatch(renderType)
        }
        projected = null; markerWorld = null
        val target = marker ?: return
        if (level.gameTime >= markerUntil || target.getString("dimension") != level.dimension().location().toString()) { marker = null; return }
        var world = Vec3(target.getDouble("x"), target.getDouble("y"), target.getDouble("z"))
        val position = BlockPos.of(target.getLong("position"))
        if (!target.getBoolean("lastKnown") && level.hasChunkAt(position)) level.getBlockEntity(position)?.let { block ->
            world = SubLevelRenderPose.apply(PoseStack(), block, position.x + .5, position.y + .5, position.z + .5, Vec3.ZERO, partial).worldPosition
        }
        markerWorld = world
        val relative = world.subtract(camera)
        projected = Vector4f(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat(), 1f)
            .mul(event.modelViewMatrix).mul(event.projectionMatrix)
    }
    @SubscribeEvent fun hud(event: RenderGuiEvent.Post) {
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.screen != null) return
        val target = marker ?: return
        if ((mc.level?.gameTime ?: Long.MAX_VALUE) >= markerUntil) return
        val point = projected ?: return
        val world = markerWorld ?: return
        val g = event.guiGraphics
        val width = mc.window.guiScaledWidth; val height = mc.window.guiScaledHeight
        val behind = point.w <= 0
        val divisor = kotlin.math.abs(point.w).coerceAtLeast(.001f)
        var dx = point.x / divisor * width / 2
        var dy = -point.y / divisor * height / 2
        if (behind && kotlin.math.abs(dx) + kotlin.math.abs(dy) < 1) dy = height.toFloat()
        val scale = maxOf(1f, kotlin.math.abs(dx) / (width / 2f - 30), kotlin.math.abs(dy) / (height / 2f - 35))
        val x = (width / 2 + dx / scale).toInt(); val y = (height / 2 + dy / scale).toInt()
        val color = if (target.getBoolean("lastKnown")) 0xffffc36a.toInt() else 0xff25ded8.toInt()
        g.drawString(mc.font, if (behind || scale > 1) "◆" else "[+]", x - 7, y - 5, color, true)
        val label = if (target.getBoolean("antenna")) Component.translatable("teutonlib.antenna.title").string else
            target.getString("nickname").ifEmpty { Component.translatable("block.teutonlib.peripheral_link").string }
        val distance = world.distanceTo(eventCamera(mc))
        val text = "$label · ${"%.1f".format(java.util.Locale.ROOT, distance)} m"
        val textX = (x - mc.font.width(text) / 2).coerceIn(3, maxOf(3, width - mc.font.width(text) - 3))
        g.drawString(mc.font, text, textX, (y + 9).coerceAtMost(height - 25), color, true)
        if (target.getBoolean("lastKnown")) {
            val last = Component.translatable("teutonlib.antenna.last_known").string
            g.drawString(mc.font, last, (x - mc.font.width(last) / 2).coerceIn(3, maxOf(3, width - mc.font.width(last) - 3)),
                (y + 20).coerceAtMost(height - 13), color, true)
        }
    }
    private fun eventCamera(mc: Minecraft): Vec3 = mc.gameRenderer.mainCamera.position
}
