package de.TeutonStudio.TeutonLib.mixin.computer.client;

import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import de.TeutonStudio.TeutonLib.integration.computer.ChannelBank;
import de.TeutonStudio.TeutonLib.integration.computer.ChannelHost;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.client.ClientWireNetworkHandler", remap = false)
public abstract class DbwClientMixin {
    @Shadow
    private static BlockPos selectedSource;

    @Shadow
    private static String currentChannel;

    @Inject(method = "changeChannel", at = @At("HEAD"), cancellable = true)
    private static void teutonlib$cycle(Block block, boolean forward, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || selectedSource == null) return;

        BlockEntity selected = minecraft.level.getBlockEntity(selectedSource);
        if (!(selected instanceof ComputerBlockEntity) || !(selected instanceof ChannelHost host)) return;

        List<String> channels = host.teutonlib$channels().entries().stream()
            .filter(entry -> entry.direction().equals("output"))
            .map(ChannelBank.Entry::name)
            .toList();
        if (channels.isEmpty()) {
            selectedSource = null;
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("teutonlib.computer.no_outputs"), true);
            }
        } else {
            int index = channels.indexOf(currentChannel);
            currentChannel = channels.get(index < 0 ? 0 : Math.floorMod(index + (forward ? 1 : -1), channels.size()));
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(currentChannel), true);
            }
        }
        ci.cancel();
    }
}
