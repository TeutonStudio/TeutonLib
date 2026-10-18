package de.TeutonStudio.TeutonLib.mixin.computer;
import de.TeutonStudio.TeutonLib.integration.computer.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(BlockEntity.class)
public abstract class ChannelHostMixin implements ChannelHost {
    @Unique private ChannelBank teutonlib$bank = new ChannelBank();
    @Override public ChannelBank teutonlib$channels() { return teutonlib$bank; }
    @Inject(method = {"saveWithoutMetadata", "saveCustomOnly"}, at = @At("RETURN"))
    private void teutonlib$save(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        if (!teutonlib$bank.entries().isEmpty()) cir.getReturnValue().put("TeutonLibChannels", ChannelPersistence.save(teutonlib$bank));
    }
    @Inject(method = {"loadWithComponents", "loadCustomOnly"}, at = @At("RETURN"))
    private void teutonlib$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        teutonlib$bank = ChannelPersistence.load(tag.getList("TeutonLibChannels", Tag.TAG_COMPOUND));
        ComputerIntegration.loaded((BlockEntity)(Object)this);
    }
    @Inject(method = "setLevel", at = @At("RETURN"))
    private void teutonlib$level(Level level, CallbackInfo ci) { ComputerIntegration.loaded((BlockEntity)(Object)this); }
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void teutonlib$remove(CallbackInfo ci) { ComputerIntegration.removed((BlockEntity)(Object)this); }
}
