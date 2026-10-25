package de.TeutonStudio.TeutonLib.mixin.mobile;

import com.mojang.blaze3d.platform.Window;
import de.TeutonStudio.TeutonLib.client.compat.MobileStencilRenderTarget;
import net.createmod.catnip.gui.UIRenderHelper.CustomRenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.createmod.catnip.gui.UIRenderHelper$CustomRenderTarget", remap = false)
public abstract class PonderRenderTargetMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true, require = 0)
    private static void teutonlib$createMobileFramebuffer(
        Window window,
        CallbackInfoReturnable<CustomRenderTarget> cir
    ) {
        cir.setReturnValue(MobileStencilRenderTarget.createMobile(window));
    }
}
