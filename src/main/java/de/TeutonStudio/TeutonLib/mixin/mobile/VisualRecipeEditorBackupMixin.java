package de.TeutonStudio.TeutonLib.mixin.mobile;

import de.TeutonStudio.TeutonLib.compat.CompatibilityDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.mcreator.visualrecipeeditor.procedures.WorkspaceBackupProcedure", remap = false)
public abstract class VisualRecipeEditorBackupMixin {
    @Inject(method = "execute()V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void teutonlib$skipDeveloperWorkspaceBackup(CallbackInfo ci) {
        CompatibilityDiagnostics.infoOnce(
            "visual-recipe-editor-backup",
            "TeutonLib: disabled Visual Recipe Editor's bundled developer-workspace backup"
        );
        ci.cancel();
    }
}
