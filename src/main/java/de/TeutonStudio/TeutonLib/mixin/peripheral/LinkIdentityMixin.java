package de.TeutonStudio.TeutonLib.mixin.peripheral;

import de.TeutonStudio.TeutonLib.integration.peripheral.LinkIdentity;
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Mixin(BlockEntity.class)
public abstract class LinkIdentityMixin implements LinkIdentity {
    @Unique private UUID teutonlib$linkIdentity;

    @Override public UUID teutonlib$linkTarget() { return teutonlib$linkIdentity; }
    @Override public void teutonlib$linkTarget(UUID id) {
        teutonlib$linkIdentity = id;
        BlockEntity self = (BlockEntity) (Object) this;
        self.setChanged();
        LinkRegistry.registerAnchor(self);
    }

    @Inject(method = {"saveWithoutMetadata", "saveCustomOnly"}, at = @At("RETURN"))
    private void teutonlib$save(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        if (teutonlib$linkIdentity != null) cir.getReturnValue().putUUID("TeutonLibLinkTarget", teutonlib$linkIdentity);
    }

    @Inject(method = {"loadWithComponents", "loadCustomOnly"}, at = @At("RETURN"))
    private void teutonlib$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        teutonlib$linkIdentity = tag.hasUUID("TeutonLibLinkTarget") ? tag.getUUID("TeutonLibLinkTarget") : null;
        LinkRegistry.registerAnchor((BlockEntity) (Object) this);
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void teutonlib$level(Level level, CallbackInfo ci) {
        LinkRegistry.registerAnchor((BlockEntity) (Object) this);
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void teutonlib$removed(CallbackInfo ci) {
        LinkRegistry.removeAnchor((BlockEntity) (Object) this);
    }
}
