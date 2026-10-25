package de.TeutonStudio.TeutonLib.mixin.mobile;

import de.TeutonStudio.TeutonLib.compat.CompatibilityDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "immersive_melodies.MidiListener", remap = false)
public abstract class ImmersiveMelodiesMidiMixin {
    @Inject(method = "launch", at = @At("HEAD"), cancellable = true, require = 0)
    private static void teutonlib$skipHardwareMidiEnumeration(CallbackInfo ci) {
        CompatibilityDiagnostics.infoOnce(
            "immersive-melodies-midi",
            "TeutonLib: hardware MIDI enumeration is unavailable on Android; file playback and Minecraft sounds remain enabled"
        );
        ci.cancel();
    }
}
