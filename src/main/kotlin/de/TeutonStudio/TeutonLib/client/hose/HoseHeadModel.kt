package de.TeutonStudio.TeutonLib.client.hose

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.HosePulleyCompatibility
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.model.BakedModelWrapper
import net.neoforged.neoforge.client.model.data.ModelData

/** Derive a head-only model from the active Create resource pack, capping its built-in straight hose. */
private class HoseHeadModel(original: BakedModel, private val height: Float) : BakedModelWrapper<BakedModel>(original) {
    private val cache = java.util.IdentityHashMap<BakedQuad,BakedQuad>()
    private fun cap(quads: List<BakedQuad>): MutableList<BakedQuad> = quads.map { quad -> cache.getOrPut(quad) {
        val data = quad.vertices.clone()
        val stride = data.size/4
        repeat(4) { index ->
            val at = index*stride+1
            val y = Float.fromBits(data[at])
            if (y > height) data[at] = height.toRawBits()
        }
        BakedQuad(data,quad.tintIndex,quad.direction,quad.sprite,quad.isShade,quad.hasAmbientOcclusion())
    } }.toMutableList()
    override fun getQuads(state: BlockState?, side: Direction?, random: RandomSource): MutableList<BakedQuad> =
        cap(originalModel.getQuads(state,side,random))
    override fun getQuads(state: BlockState?, side: Direction?, random: RandomSource, data: ModelData, renderType: RenderType?): MutableList<BakedQuad> =
        cap(originalModel.getQuads(state,side,random,data,renderType))
}

@EventBusSubscriber(modid = TeutonLib.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object HoseHeadModelRegistration {
    @JvmField val HEAD = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID,"block/hose_head"))
    @JvmField val SEATED = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID,"block/hose_head_seated"))
    private val original = ModelResourceLocation.standalone(ResourceLocation.parse("create:block/hose_pulley/pulley_magnet"))
    @SubscribeEvent fun register(event: ModelEvent.RegisterAdditional) {
        if (HosePulleyCompatibility.active()) event.register(original)
    }
    @SubscribeEvent fun bake(event: ModelEvent.ModifyBakingResult) {
        if (HosePulleyCompatibility.active()) event.models[original]?.let {
            event.models[HEAD] = HoseHeadModel(it,5f/16f)
            // Keep a short rigid neck above the supplied drain's 13/16-high grating.
            event.models[SEATED] = HoseHeadModel(it,.5f)
        }
    }
}
