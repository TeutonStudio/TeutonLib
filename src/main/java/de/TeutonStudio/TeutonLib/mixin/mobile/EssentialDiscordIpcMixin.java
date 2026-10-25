package de.TeutonStudio.TeutonLib.mixin.mobile;

import de.TeutonStudio.TeutonLib.compat.CompatibilityDiagnostics;
import de.TeutonStudio.TeutonLib.compat.EssentialSocketAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "gg.essential.util.kdiscordipc.KDiscordIPCLoader", remap = false)
public abstract class EssentialDiscordIpcMixin {
    @Inject(method = "getPlatformSocket", at = @At("HEAD"), cancellable = true, require = 0)
    private void teutonlib$disabledAndroidSocket(CallbackInfoReturnable<Object> cir) {
        if (!EssentialSocketAdapter.isVerifiedVersion()) return;
        CompatibilityDiagnostics.infoOnce(
            "essential-discord-ipc",
            "TeutonLib: local Discord IPC is unavailable on Android; Essential will use its normal disconnected state"
        );
        cir.setReturnValue(EssentialSocketAdapter.create());
    }
}
