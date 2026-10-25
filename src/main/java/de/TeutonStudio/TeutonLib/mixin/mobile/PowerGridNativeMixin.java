package de.TeutonStudio.TeutonLib.mixin.mobile;

import de.TeutonStudio.TeutonLib.compat.CompatibilityDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.patryk3211.powergrid.electricity.sim.solver.NativeMNA", remap = false)
public abstract class PowerGridNativeMixin {
    @Inject(method = "tryLoad", at = @At("HEAD"), cancellable = true, require = 0)
    private static void teutonlib$useJavaSolver(CallbackInfo ci) {
        CompatibilityDiagnostics.infoOnce(
            "powergrid-java-solver",
            "TeutonLib: skipping PowerGrid's x86 desktop native loader; JavaMNA remains active"
        );
        ci.cancel();
    }
}
