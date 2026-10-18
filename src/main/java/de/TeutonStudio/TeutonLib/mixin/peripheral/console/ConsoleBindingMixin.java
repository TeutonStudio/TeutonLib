package de.TeutonStudio.TeutonLib.mixin.peripheral.console;

import dan200.computercraft.api.lua.*;
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry;
import de.teutonstudio.ccaeroworks.computer.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PeripheralBinding.class, remap = false)
public abstract class ConsoleBindingMixin {
    @Shadow private PeripheralNetworkNode node;
    @Shadow private boolean attached;
    @Unique private boolean teutonlib$closing;
    @Unique private boolean teutonlib$invalidated;
    private boolean teutonlib$validLink() {
        if (!node.getAddress().startsWith("cc_aeroworks:link_")) return true;
        if (teutonlib$invalidated) return false;
        LinkRegistry.Connection current = LinkRegistry.connection(node.getAddress());
        if (current == null) { teutonlib$invalidated = true; return false; }
        if (current.link().getLevel().getServer().isSameThread()) { LinkRegistry.validate(); current = current.link().connection; }
        boolean valid = current != null && current.anchor() == node.getDesk().getDesk()
                && (current.peripheral() == node.getTarget() || current.peripheral().equals(node.getTarget()));
        if (!valid) teutonlib$invalidated = true;
        return valid;
    }
    @Inject(method = "checkAttached", at = @At("HEAD"))
    private void teutonlib$check(CallbackInfo ci) {
        if (!teutonlib$closing && !teutonlib$validLink()) throw new dan200.computercraft.api.peripheral.NotAttachedException();
    }
    @Inject(method = "call", at = @At("HEAD"))
    private void teutonlib$call(ILuaContext context, String name, IArguments arguments,
                               CallbackInfoReturnable<MethodResult> cir) throws LuaException {
        if (node.getAddress().startsWith("cc_aeroworks:link_") && (!attached || !teutonlib$validLink()))
            throw new LuaException("Peripheral is no longer attached");
    }
    @Inject(method = "checkValid", at = @At("RETURN"), cancellable = true)
    private void teutonlib$guard(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !teutonlib$validLink()) cir.setReturnValue(false);
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void teutonlib$closing(CallbackInfo ci) { teutonlib$closing = true; }
    @Inject(method = "close", at = @At("RETURN"))
    private void teutonlib$closed(CallbackInfo ci) { teutonlib$closing = false; }
}
