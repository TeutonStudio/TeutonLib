package de.TeutonStudio.TeutonLib.client.energy

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.energy.FluxPointConnections
import de.TeutonStudio.TeutonLib.integration.energy.MotorExtensionConnectionGeometry
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.BlockElementFace
import net.minecraft.client.renderer.block.model.BlockFaceUV
import net.minecraft.client.renderer.block.model.FaceBakery
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.BlockModelRotation
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.model.BakedModelWrapper
import net.neoforged.neoforge.client.model.data.ModelData
import net.neoforged.neoforge.client.model.data.ModelProperty
import org.joml.Vector3f

/** Neighbor orientation chooses the casing profile without multiplying the saved blockstates. */
class FluxPointModel(original: BakedModel, template: BakedModel) : BakedModelWrapper<BakedModel>(original) {
    private val textures=template.getQuads(null,null,RandomSource.create()).distinctBy { it.tintIndex }
    private val arms=java.util.concurrent.ConcurrentHashMap<Pair<Direction,Direction>,List<BakedQuad>>()
    override fun getModelData(level: BlockAndTintGetter, pos: BlockPos, state: BlockState, data: ModelData): ModelData =
        originalModel.getModelData(level,pos,state,data).derive().with(FACINGS,FluxPointConnections.facings(state,level,pos)).build()

    override fun getQuads(state: BlockState?, side: Direction?, random: RandomSource): MutableList<BakedQuad> =
        getQuads(state,side,random,ModelData.EMPTY,null)

    override fun getQuads(state: BlockState?, side: Direction?, random: RandomSource, data: ModelData, renderType: RenderType?): MutableList<BakedQuad> {
        val quads=originalModel.getQuads(state,side,random,data,renderType).toMutableList()
        if (state == null || side != null || renderType != null && renderType != RenderType.cutout()) return quads
        val facings=data.get(FACINGS) ?: FluxPointConnections.facings(state,null,BlockPos.ZERO)
        for (direction in Direction.entries) if (FluxPointConnections.specialized(state,direction)) {
            val facing=facings[direction.ordinal] ?: direction
            quads+=arms.getOrPut(direction to facing) { bake(direction,facing) }
        }
        return quads
    }

    private fun bake(side: Direction, facing: Direction): List<BakedQuad> {
        val bakery=FaceBakery()
        return MotorExtensionConnectionGeometry.arm(side,facing).flatMapIndexed { index,box ->
            textures.flatMap { texture -> Direction.entries.map { face ->
                val coordinates=listOf(box.minX,box.minY,box.minZ)
                val maxima=listOf(box.maxX,box.maxY,box.maxZ)
                val cross=(0..2).filter { it != side.axis.ordinal }
                val uv=if (index == 0) {
                    if (face.axis == side.axis) floatArrayOf(0f,0f,2f,2f)
                    else floatArrayOf(0f,0f,2f,(maxima[side.axis.ordinal]-coordinates[side.axis.ordinal]).toFloat())
                } else if (face.axis == side.axis) floatArrayOf(
                    (coordinates[cross[0]]+5).toFloat(),(coordinates[cross[1]]+5).toFloat(),
                    (maxima[cross[0]]+5).toFloat(),(maxima[cross[1]]+5).toFloat())
                else floatArrayOf(0f,0f,(maxima[cross.first { it != face.axis.ordinal }]-coordinates[cross.first { it != face.axis.ordinal }]).toFloat(),1f)
                bakery.bakeQuad(Vector3f(box.minX.toFloat(),box.minY.toFloat(),box.minZ.toFloat()),
                    Vector3f(box.maxX.toFloat(),box.maxY.toFloat(),box.maxZ.toFloat()),
                    BlockElementFace(null,texture.tintIndex,"",BlockFaceUV(uv,0)),texture.sprite,face,BlockModelRotation.X0_Y0,null,texture.isShade)
            } }
        }
    }
    companion object { @JvmField val FACINGS=ModelProperty<List<Direction?>>() }
}

@EventBusSubscriber(modid = TeutonLib.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object FluxPointModelRegistration {
    private val template=ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(TeutonLib.MOD_ID,"block/flux_connection_rear"))
    private fun active() = ModList.get().isLoaded("fluxnetworks") && ModList.get().isLoaded("create_new_age")
    @SubscribeEvent fun register(event: ModelEvent.RegisterAdditional) { if (active()) event.register(template) }
    @SubscribeEvent fun bake(event: ModelEvent.ModifyBakingResult) {
        if (!active()) return
        val source=checkNotNull(event.models[template])
        val wrapped=java.util.IdentityHashMap<BakedModel,FluxPointModel>()
        for ((key,model) in event.models.toMap()) if (key.id == ResourceLocation.parse("fluxnetworks:flux_point") && key.variant != "inventory")
            event.models[key]=wrapped.getOrPut(model) { FluxPointModel(model,source) }
    }
}
