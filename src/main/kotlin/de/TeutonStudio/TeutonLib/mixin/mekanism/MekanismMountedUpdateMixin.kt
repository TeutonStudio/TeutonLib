package de.TeutonStudio.TeutonLib.mixin.mekanism

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import mekanism.common.network.PacketUtils
import mekanism.common.network.to_client.PacketUpdateTile
import mekanism.common.tile.transmitter.TileEntityTransmitter
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo

@Pseudo
@Mixin(targets = ["mekanism.common.tile.base.TileEntityUpdateable"], priority = 1100, remap = false)
abstract class MekanismMountedUpdateMixin {
    @WrapMethod(method = ["sendUpdatePacket(Lnet/minecraft/world/level/block/entity/BlockEntity;)V"])
    private fun `teutonlib$addressMountedTransmitter`(trackingTile: BlockEntity, original: Operation<Void>) {
        val pipe = this as Any as? TileEntityTransmitter
        val level = pipe?.level as? ServerLevel
        val sub = pipe?.let { Sable.HELPER.getContaining(it) } as? ServerSubLevel
        if (pipe == null || level == null || sub == null || trackingTile !== pipe || pipe.isRemoved ||
            sub.isRemoved || level.getBlockEntity(pipe.blockPos) !== pipe) {
            original.call(trackingTile)
            return
        }

        // CMC 0.1.21 sends the projected world cell, then guesses the pipe from the client's
        // current pose. Motion can make that cell resolve to a neighbor or no tile at all.
        // The plot address and its native Sable recipients identify this exact block throughout.
        val pos = pipe.blockPos
        if (PacketUtils.hasPlayersTracking(level, pos)) {
            PacketUtils.sendToAllTracking(PacketUpdateTile(pos, pipe.getUpdateTag(level.registryAccess())), level, pos)
        }
    }
}
