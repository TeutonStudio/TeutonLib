package de.TeutonStudio.TeutonLib.mixin.computer.client;

import de.TeutonStudio.TeutonLib.integration.computer.ChannelHost;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="edn.stratodonut.drivebywire.client.ClientWireNetworkHandler",remap=false)
public abstract class DbwClientMixin {
    @Shadow private static BlockPos selectedSource;
    @Shadow private static String currentChannel;
    @Inject(method="changeChannel",at=@At("HEAD"),cancellable=true)
    private static void teutonlib$cycle(Block block,boolean forward,CallbackInfo ci) {
        var mc=Minecraft.getInstance();
        if(mc.level==null || selectedSource==null || !(mc.level.getBlockEntity(selectedSource) instanceof ComputerBlockEntity host)) return;
        var channels=((ChannelHost)host).teutonlib$channels().entries().stream().filter(c -> c.direction().equals("output")).map(c -> c.name()).toList();
        if(channels.isEmpty()) {
            selectedSource=null;
            if(mc.player!=null) mc.player.displayClientMessage(Component.translatable("teutonlib.computer.no_outputs"),true);
        } else {
            int index=channels.indexOf(currentChannel);
            currentChannel=channels.get(index<0?0:Math.floorMod(index+(forward?1:-1),channels.size()));
            if(mc.player!=null) mc.player.displayClientMessage(Component.literal(currentChannel),true);
        }
        ci.cancel();
    }
}
