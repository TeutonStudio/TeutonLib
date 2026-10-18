package de.TeutonStudio.TeutonLib.integration.peripheral;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class SableLinkPositions {
    static Vec3 world(ServerLevel level, BlockPos pos) {
        var sublevel = Sable.HELPER.getContaining(level, pos);
        return sublevel == null ? pos.getCenter() : sublevel.logicalPose().transformPosition(pos.getCenter());
    }
    private SableLinkPositions() {}
}
