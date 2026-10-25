package de.TeutonStudio.TeutonLib.mixin.resource;

import de.TeutonStudio.TeutonLib.compat.resources.ResourceCompatibility;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.neoforged.neoforge.resource.ResourcePackLoader", remap = false)
public abstract class ResourcePackLoaderMixin {
    @Inject(method = "createPackForMod", at = @At("RETURN"), cancellable = true)
    private static void teutonlib$decorateKnownResources(
        IModFileInfo modFile,
        CallbackInfoReturnable<Pack.ResourcesSupplier> cir
    ) {
        cir.setReturnValue(ResourceCompatibility.wrap(modFile, cir.getReturnValue()));
    }
}
