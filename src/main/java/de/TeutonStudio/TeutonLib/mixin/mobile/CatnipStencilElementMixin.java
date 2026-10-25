package de.TeutonStudio.TeutonLib.mixin.mobile;

import de.TeutonStudio.TeutonLib.client.compat.ColorMaskFallback;
import net.createmod.catnip.gui.element.StencilElement;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.createmod.catnip.gui.element.StencilElement", remap = false)
public interface CatnipStencilElementMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void teutonlib$renderWithColorMask(GuiGraphics graphics, CallbackInfo ci) {
        if (!ColorMaskFallback.isActive()) return;
        ColorMaskFallback.render((StencilElement) this, graphics);
        ci.cancel();
    }
}
