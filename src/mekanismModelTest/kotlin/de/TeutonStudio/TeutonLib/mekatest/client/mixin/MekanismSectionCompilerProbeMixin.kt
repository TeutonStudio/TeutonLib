package de.TeutonStudio.TeutonLib.mekatest.client.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import de.TeutonStudio.TeutonLib.mekatest.client.MekanismModelProbe
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.BlockRenderDispatcher
import net.minecraft.client.renderer.chunk.SectionCompiler
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.client.model.data.ModelData
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/** Observation only: every native call and return value is preserved. */
@Mixin(SectionCompiler::class)
abstract class MekanismSectionCompilerProbeMixin {
    @WrapOperation(method = ["compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler\$Results;"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/BakedModel;getModelData(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/neoforged/neoforge/client/model/data/ModelData;)Lnet/neoforged/neoforge/client/model/data/ModelData;")])
    private fun teutonlibTestModelData(model: BakedModel, view: BlockAndTintGetter, pos: BlockPos,
        state: BlockState, input: ModelData, original: Operation<ModelData>): ModelData {
        val output = original.call(model, view, pos, state, input)
        MekanismModelProbe.recordCompile(view, pos, state, model, input, output)
        return output
    }

    @WrapOperation(method = ["compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler\$Results;"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V")])
    private fun teutonlibTestRenderContext(dispatcher: BlockRenderDispatcher, state: BlockState, pos: BlockPos,
        view: BlockAndTintGetter, poses: PoseStack, vertices: VertexConsumer, checkSides: Boolean,
        random: RandomSource, data: ModelData, layer: RenderType, original: Operation<Void>) {
        val previous = MekanismModelProbe.beginRender(view, pos)
        try { original.call(dispatcher, state, pos, view, poses, vertices, checkSides, random, data, layer) }
        finally { MekanismModelProbe.endRender(previous) }
    }
}
