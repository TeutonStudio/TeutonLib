package de.TeutonStudio.TeutonLib.client.hose

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.HosePulleyCompatibility
import de.TeutonStudio.TeutonLib.integration.hose.HoseDrainConnection
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.ChunkRenderTypeSet
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.model.BakedModelWrapper
import net.neoforged.neoforge.client.model.data.ModelData
import net.neoforged.neoforge.client.model.data.ModelProperty

/** Use the supplied drain geometry; the live head is still drawn by its entity renderer. */
class HoseDrainModel(original: BakedModel, private val normal: BakedModel, private val locked: BakedModel) : BakedModelWrapper<BakedModel>(original) {
    private fun model(data: ModelData) = if (data.get(LOCKED) == true) locked else normal

    override fun getModelData(level: BlockAndTintGetter, pos: BlockPos, state: BlockState, data: ModelData): ModelData =
        data.derive().with(LOCKED, HoseDrainConnection.get(level.getBlockEntity(pos))?.headId != null).build()

    override fun getQuads(state: BlockState?, side: Direction?, random: RandomSource, data: ModelData, renderType: RenderType?): MutableList<BakedQuad> =
        model(data).getQuads(state, side, random, data, renderType)

    override fun getRenderTypes(state: BlockState, random: RandomSource, data: ModelData): ChunkRenderTypeSet =
        model(data).getRenderTypes(state, random, data)

    companion object {
        @JvmField val LOCKED = ModelProperty<Boolean>()
    }
}

@EventBusSubscriber(modid = TeutonLib.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object HoseDrainModelRegistration {
    private val normal = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "block/item_drain/normal"))
    private val locked = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID, "block/item_drain/locked"))

    @SubscribeEvent
    fun register(event: ModelEvent.RegisterAdditional) {
        if (!HosePulleyCompatibility.active()) return
        event.register(normal)
        event.register(locked)
    }

    @SubscribeEvent
    fun bake(event: ModelEvent.ModifyBakingResult) {
        if (!HosePulleyCompatibility.active()) return
        val key = ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("create", "item_drain"), "")
        val original = event.models[key] ?: return
        event.models[key] = HoseDrainModel(original, checkNotNull(event.models[normal]), checkNotNull(event.models[locked]))
    }
}
