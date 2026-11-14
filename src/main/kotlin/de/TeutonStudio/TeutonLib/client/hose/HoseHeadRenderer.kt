package de.TeutonStudio.TeutonLib.client.hose

import com.mojang.blaze3d.vertex.PoseStack
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.HosePulleyCompatibility
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadEntity
import de.TeutonStudio.TeutonLib.integration.hose.HoseEntities
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent

/** Geometry is drawn once by HoseCableClient, even when the entity is outside tracking range. */
class HoseHeadRenderer(context: EntityRendererProvider.Context) : EntityRenderer<HoseHeadEntity>(context) {
    override fun render(entity: HoseHeadEntity, yaw: Float, partialTick: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        super.render(entity,yaw,partialTick,pose,buffers,light)
    }
    override fun getTextureLocation(entity: HoseHeadEntity): ResourceLocation = TextureAtlas.LOCATION_BLOCKS
}

@EventBusSubscriber(modid = TeutonLib.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object HoseHeadRendererRegistration {
    @SubscribeEvent fun register(event: EntityRenderersEvent.RegisterRenderers) {
        if (HosePulleyCompatibility.active()) event.registerEntityRenderer(HoseEntities.HOSE_HEAD.get(), ::HoseHeadRenderer)
    }
}
