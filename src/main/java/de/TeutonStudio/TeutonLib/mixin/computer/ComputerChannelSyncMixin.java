package de.TeutonStudio.TeutonLib.mixin.computer;
import de.TeutonStudio.TeutonLib.integration.computer.*;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value = ComputerBlockEntity.class, remap = false)
public abstract class ComputerChannelSyncMixin {
    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void teutonlib$update(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().put("TeutonLibChannels", ChannelPersistence.save(((ChannelHost)this).teutonlib$channels()));
    }
}
