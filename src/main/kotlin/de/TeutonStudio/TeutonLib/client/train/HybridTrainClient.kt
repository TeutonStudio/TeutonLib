package de.TeutonStudio.TeutonLib.client.train

import com.mojang.blaze3d.vertex.PoseStack
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.HybridTrainCompatibility
import de.TeutonStudio.TeutonLib.network.HybridTrainStatePayload
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.CreativeModeTab
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import org.joml.Matrix4f
import org.joml.Quaternionf
import java.util.UUID

object HybridTrainClient {
    private var state: HybridTrainStatePayload? = null
    private val creativeCache by lazy {
        net.minecraft.world.item.CreativeModeTabs::class.java.getDeclaredField("CACHED_PARAMETERS").also { it.isAccessible = true }
    }
    fun enabled() = HybridTrainCompatibility.active() && (state?.enabled ?: ServerConfig.hybridTrains())
    fun driving(id: UUID) = state?.let { it.enabled && id in it.driving } == true

    fun apply(next: HybridTrainStatePayload) {
        val before = enabled()
        state = next
        if (before != enabled()) invalidateTabs()
    }

    fun clear() { state = null; invalidateTabs() }

    private fun invalidateTabs() {
        // Vanilla caches by flags/permissions/registries, none of which changes
        // when a server reloads its train config.
        creativeCache.set(null, null)
    }

    fun creative(event: BuildCreativeModeTabContentsEvent) {
        if (!enabled()) return
        val hidden = setOf("functional_fob", "chunk_anchor_lever")
        val entries = (event.parentEntries + event.searchEntries).distinct()
        for (stack in entries) {
            val id = BuiltInRegistries.ITEM.getKey(stack.item)
            if (id.namespace == "create_train_functionality" && id.path in hidden) {
                event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS)
            }
        }
    }

    fun update(sub: ClientSubLevel) {
        if (driving(sub.uniqueId) != (sub.renderData is ContraptionLevel)) sub.updateRenderData()
    }

    fun refreshEmbeddings(level: net.minecraft.world.level.Level) {
        val snapshot = state?.takeIf { it.enabled } ?: return
        val container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level) ?: return
        for (id in snapshot.driving) {
            val sub = container.getSubLevel(id) ?: continue
            if (!sub.isRemoved) dev.ryanhcode.sable.neoforge.compatibility.flywheel.FlywheelCompatNeoForge.createRenderInfo(level, sub)
        }
    }

    fun outline(sub: ClientSubLevel, modelView: Matrix4f, buffers: MultiBufferSource, camera: net.minecraft.world.phys.Vec3) {
        if (sub.renderData !is ContraptionLevel) return
        val bounds = sub.plot.boundingBox
        if (bounds.volume() <= 0) return
        val pose = sub.renderPose()
        val stack = PoseStack()
        stack.mulPose(modelView)
        val position = pose.position()
        stack.translate(position.x() - camera.x, position.y() - camera.y, position.z() - camera.z)
        stack.mulPose(Quaternionf(pose.orientation()))
        val scale = pose.scale()
        stack.scale(scale.x().toFloat(), scale.y().toFloat(), scale.z().toFloat())
        val center = pose.rotationPoint()
        LevelRenderer.renderLineBox(stack, buffers.getBuffer(RenderType.lines()),
            bounds.minX() - center.x() - .004, bounds.minY() - center.y() - .004, bounds.minZ() - center.z() - .004,
            bounds.maxX() + 1.004 - center.x(), bounds.maxY() + 1.004 - center.y(), bounds.maxZ() + 1.004 - center.z(),
            1f, .5f, 0f, 1f)
    }
}
