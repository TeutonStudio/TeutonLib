package de.TeutonStudio.TeutonLib.mixin.computer.client

import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import de.TeutonStudio.TeutonLib.integration.computer.ChannelBank
import de.TeutonStudio.TeutonLib.integration.computer.ChannelHost
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Block
import org.spongepowered.asm.mixin.*
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Pseudo
@Mixin(targets = ["edn.stratodonut.drivebywire.client.ClientWireNetworkHandler"], remap = false)
abstract class DbwClientMixin {
    private companion object {
        @Shadow
        @JvmField
        var selectedSource: BlockPos? = null

        @Shadow
        @JvmField
        var currentChannel: String? = null

        @Inject(method = ["changeChannel"], at = [At("HEAD")], cancellable = true)
        @JvmStatic
        private fun `teutonlib$cycle`(block: Block, forward: Boolean, ci: CallbackInfo) {
            val mc = Minecraft.getInstance()
            if (mc.level == null || selectedSource == null || (mc.level!!.getBlockEntity(selectedSource) !is ComputerBlockEntity)) return
            val host = mc.level!!.getBlockEntity(selectedSource) as ComputerBlockEntity
            val channels: MutableList<String> = (host as ChannelHost).`teutonlib$channels`().entries().stream()
                .filter { c: ChannelBank.Entry? -> c!!.direction == "output" }
                .map<String> { c: ChannelBank.Entry? -> c!!.name }.toList()
            if (channels.isEmpty()) {
                selectedSource = null
                if (mc.player != null) mc.player!!.displayClientMessage(
                    Component.translatable("teutonlib.computer.no_outputs"),
                    true
                )
            } else {
                val index = channels.indexOf(currentChannel)
                currentChannel =
                    channels.get(if (index < 0) 0 else Math.floorMod(index + (if (forward) 1 else -1), channels.size))
                if (mc.player != null) mc.player!!.displayClientMessage(Component.literal(currentChannel), true)
            }
            ci.cancel()
        }
    }
}
