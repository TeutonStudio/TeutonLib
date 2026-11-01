package de.TeutonStudio.TeutonLib.mixin.mekanism

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Pseudo
@Mixin(targets = ["mekanism.common.network.PacketUtils"], remap = false)
abstract class MekanismPacketTrackingMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["hasPlayersTracking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Z"],
            at = [At("HEAD")], cancellable = true)
        private fun `teutonlib$useActualChunkRecipients`(level: ServerLevel, pos: BlockPos,
            cir: CallbackInfoReturnable<Boolean>) {
            // IP replaces vanilla ChunkTrackingView updates. Use the same recipient lookup as
            // Mekanism's subsequent PacketDistributor call; it also includes Sable plot viewers.
            cir.returnValue = level.chunkSource.chunkMap.getPlayers(ChunkPos(pos), false).isNotEmpty()
        }
    }
}
