package de.TeutonStudio.TeutonLib.integration.peripheral;

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Isolates all optional Aeroworks type checks from the CC-only registry. */
final class ConsoleLinkSupport {
    static boolean isTarget(BlockEntity block) { return block instanceof ConsoleBlockEntity; }
    static BlockEntity owner(BlockEntity anchor) {
        if (!(anchor instanceof ConsoleBlockEntity)) return null;
        var snapshot = ConsoleMultiblockManager.INSTANCE.resolve(anchor.getLevel(), anchor.getBlockPos());
        if (snapshot.getState() != de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState.ACTIVE) return null;
        var owner = snapshot.getOwner();
        return owner == null ? null : owner.getHost();
    }
    private ConsoleLinkSupport() {}
}
