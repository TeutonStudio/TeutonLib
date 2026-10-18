package de.TeutonStudio.TeutonLib.peripheraltest;

import de.TeutonStudio.TeutonLib.integration.peripheral.*;
import de.TeutonStudio.TeutonLib.integration.computer.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import java.util.List;
import java.util.UUID;

/** Uses Sable's actual block assembly/move implementation, not simulated coordinates. */
final class SableVerification {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static int dbwRead(ServerLevel level, BlockPos pos) {
        try {
            var type=Class.forName("edn.stratodonut.drivebywire.wire.WireNetworkManager");
            Object manager=type.getMethod("get", net.minecraft.world.level.Level.class).invoke(null,level);
            type.getMethod("flushPendingGraphRebuild",net.minecraft.world.level.Level.class).invoke(manager,level);
            return DbwAccess.read(level.getBlockEntity(pos), "top");
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static String run(ServerLevel level) {
        BlockPos source = new BlockPos(72, 220, 8), linkSource = source.east(4);
        level.setChunkForced(4, 0, true);
        level.setBlockAndUpdate(source, dan200.computercraft.shared.ModRegistry.Blocks.COMPUTER_NORMAL.get().defaultBlockState());
        var anchor = level.getBlockEntity(source);
        var bank = ((ChannelHost)anchor).teutonlib$channels();
        bank.create("flight", "output", ""); bank.set("flight", 11); bank.published("flight", true); anchor.setChanged();
        DbwAccess.set(anchor, "flight", 11);
        UUID targetId = UUID.randomUUID(); ((LinkIdentity) anchor).teutonlib$linkTarget(targetId);
        level.setBlockAndUpdate(linkSource.below(), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(linkSource, PeripheralLinks.BLOCK.get().defaultBlockState());
        var link = (PeripheralLinkBlockEntity) level.getBlockEntity(linkSource);
        link.target(targetId); LinkRegistry.register(link); LinkRegistry.validate();
        check(link.connection != null, "stationary Sable fixture must connect");
        UUID linkId = link.identity();
        if (DbwAccess.available()) {
            try {
                var manager = Class.forName("edn.stratodonut.drivebywire.wire.WireNetworkManager");
                manager.getMethod("createConnection", net.minecraft.world.level.Level.class, BlockPos.class, BlockPos.class, net.minecraft.core.Direction.class, String.class)
                    .invoke(null, level, source, linkSource.below(), net.minecraft.core.Direction.UP, "flight");
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            check(dbwRead(level,linkSource.below()) == 11, "stationary DbW channel must deliver value");
        }
        var anchorShip = SubLevelAssemblyHelper.assembleBlocks(level, source, List.of(source), new BoundingBox3i(source, source));
        BlockPos anchorPos = anchorShip.getPlot().getCenterBlock();
        anchor = level.getBlockEntity(anchorPos);
        LinkRegistry.validate();
        check(targetId.equals(((LinkIdentity) anchor).teutonlib$linkTarget()), "assembly must preserve target identity");
        check(((ChannelHost)anchor).teutonlib$channels().get("flight").value() == 11 && ((ChannelHost)anchor).teutonlib$channels().get("flight").published(), "assembly preserves channel definitions and values");
        if (DbwAccess.available()) check(DbwAccess.connections(anchor, "flight") == 1, "assembly moves DbW output connections");
        check(link.connection != null, "world to assembled target must connect");
        var linkShip = SubLevelAssemblyHelper.assembleBlocks(level, linkSource, List.of(linkSource, linkSource.below()),
                new BoundingBox3i(linkSource.below(), linkSource));
        BlockPos linkPos = linkShip.getPlot().getCenterBlock();
        link = (PeripheralLinkBlockEntity) level.getBlockEntity(linkPos);
        LinkRegistry.validate();
        check(linkId.equals(link.identity()) && targetId.equals(link.target()), "assembly must preserve link identity and pairing");
        check(link.connection != null, "separate sublevels must connect by world distance");
        if (DbwAccess.available()) check(dbwRead(level,linkPos.below()) == 11, "DbW signals cross separate assembled sublevels");
        var savedPose = new dev.ryanhcode.sable.companion.math.Pose3d(linkShip.logicalPose());
        linkShip.logicalPose().orientation().rotateY(Math.PI / 2); LinkRegistry.validate();
        check(link.connection != null, "rotated sublevel must remain connected");
        linkShip.logicalPose().position().add(30, 10, -20); LinkRegistry.validate();
        check(link.connection != null, "moving sublevel inside range must remain connected");
        if (DbwAccess.available()) check(dbwRead(level,linkPos.below()) == 11, "DbW signals survive motion and rotation");
        linkShip.logicalPose().position().add(350, 0, 0); LinkRegistry.validate();
        check(link.connection == null && link.status.equals("range"), "moving sublevel beyond range must disconnect");
        linkShip.logicalPose().set(savedPose); LinkRegistry.validate();
        check(link.connection != null, "returning sublevel must reconnect");
        // Disassembly uses the inverse block move with all original NBT intact.
        SubLevelAssemblyHelper.moveBlocks(level, new AssemblyTransform(anchorPos, source, 0, Rotation.NONE, level), List.of(anchorPos));
        SubLevelAssemblyHelper.moveBlocks(level, new AssemblyTransform(linkPos, linkSource, 0, Rotation.NONE, level), List.of(linkPos, linkPos.below()));
        anchor = level.getBlockEntity(source); link = (PeripheralLinkBlockEntity) level.getBlockEntity(linkSource);
        LinkRegistry.validate();
        check(targetId.equals(((LinkIdentity) anchor).teutonlib$linkTarget()) && linkId.equals(link.identity()), "disassembly must preserve both identities");
        check(link.connection != null, "disassembled blocks must reconnect");
        check(((ChannelHost)anchor).teutonlib$channels().get("flight").value() == 11, "disassembly preserves channel values");
        if (DbwAccess.available()) check(dbwRead(level,linkSource.below()) == 11, "DbW signals survive disassembly");
        var container = SubLevelContainer.getContainer(level);
        container.removeSubLevel(anchorShip, SubLevelRemovalReason.REMOVED); container.removeSubLevel(linkShip, SubLevelRemovalReason.REMOVED);
        level.removeBlock(source, false); level.removeBlock(linkSource, false); level.removeBlock(linkSource.below(), false);
        level.setChunkForced(4, 0, false);
        return "PASS sable: assembly, disassembly, two sublevels, rotation, movement, range and reconnection";
    }
}
