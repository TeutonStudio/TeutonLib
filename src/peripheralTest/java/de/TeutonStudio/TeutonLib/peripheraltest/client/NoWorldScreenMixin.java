package de.TeutonStudio.TeutonLib.peripheraltest.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Only in the opt-in development verification mod, never in the published library. */
@Mixin(AbstractContainerScreen.class)
public abstract class NoWorldScreenMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void teutonlib$withoutWorld(CallbackInfo ci) {
        if(Boolean.getBoolean("teutonlib.computerUiTests") && Minecraft.getInstance().player==null) ci.cancel();
    }
}
