package de.TeutonStudio.TeutonLib.mekatest.client.mixin

import de.TeutonStudio.TeutonLib.mekatest.client.MekanismModelProbe
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.client.model.data.ModelData
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(targets = ["mekanism.client.render.obj.TransmitterBakedModel"], remap = false)
abstract class MekanismTransmitterQuadProbeMixin {
    @Inject(method = ["getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;"],
        at = [At("RETURN")])
    private fun teutonlibTestQuads(state: BlockState?, side: Direction?, random: RandomSource,
        data: ModelData, layer: RenderType?, cir: CallbackInfoReturnable<List<BakedQuad>>) {
        MekanismModelProbe.recordQuads(side, layer, data, cir.returnValue)
    }
}
