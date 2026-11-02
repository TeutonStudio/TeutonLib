package de.TeutonStudio.TeutonLib.mixin.hybrid.client

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import de.TeutonStudio.TeutonLib.client.train.ContraptionLevel
import de.TeutonStudio.TeutonLib.client.train.HybridTrainClient
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(targets = ["dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher",
    "dev.ryanhcode.sable.sublevel.render.dispatcher.ReachAroundSubLevelRenderDispatcher"], remap = false)
abstract class ContraptionLevelFactoryMixin {
    @WrapOperation(method = ["createRenderData"], at = [At(value = "NEW",
        target = "(Ldev/ryanhcode/sable/sublevel/ClientSubLevel;Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;)Ldev/ryanhcode/sable/sublevel/render/vanilla/VanillaChunkedSubLevelRenderData;")], require = 1)
    private fun `teutonlib$graphicalLevel`(sub: ClientSubLevel, dispatcher: SectionRenderDispatcher,
        original: Operation<VanillaChunkedSubLevelRenderData>): VanillaChunkedSubLevelRenderData =
        if (HybridTrainClient.driving(sub.uniqueId)) ContraptionLevel(sub, dispatcher) else original.call(sub, dispatcher)
}
