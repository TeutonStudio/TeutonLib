package de.TeutonStudio.TeutonLib.peripheraltest.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.*
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Only in the opt-in development verification mod, never in the published library.  */
@Mixin(AbstractContainerScreen::class)
abstract class NoWorldScreenMixin {
    @Inject(method = ["tick"], at = [At("HEAD")], cancellable = true)
    private fun `teutonlib$withoutWorld`(ci: CallbackInfo) {
        if (java.lang.Boolean.getBoolean("teutonlib.computerUiTests") && Minecraft.getInstance().player == null) ci.cancel()
    }

    /** Synthetic menus have no game mode; physical mouse clicks must not trigger inventory packets. */
    @Inject(method = ["slotClicked"], at = [At("HEAD")], cancellable = true)
    private fun `teutonlib$withoutInventoryPackets`(
        slot: net.minecraft.world.inventory.Slot?, slotId: Int, button: Int,
        click: net.minecraft.world.inventory.ClickType, ci: CallbackInfo
    ) {
        if ((java.lang.Boolean.getBoolean("teutonlib.computerUiTests") || java.lang.Boolean.getBoolean("teutonlib.computerUiConfigTests"))
            && Minecraft.getInstance().gameMode == null) ci.cancel()
    }
}
