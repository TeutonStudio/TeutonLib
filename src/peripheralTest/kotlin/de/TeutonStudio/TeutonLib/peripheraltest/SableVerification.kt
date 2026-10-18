package de.TeutonStudio.TeutonLib.peripheraltest

import dan200.computercraft.shared.ModRegistry
import de.TeutonStudio.TeutonLib.integration.computer.ChannelHost
import de.TeutonStudio.TeutonLib.integration.computer.DbwAccess
import de.TeutonStudio.TeutonLib.integration.peripheral.*
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkIdentity
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinkBlockEntity
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinks
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import java.lang.AssertionError
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation

/** Uses Sable's actual block assembly/move implementation, not simulated coordinates.  */
object SableVerification {
    @JvmStatic
    private fun check(value: Boolean, message: String?) {
        if (!value) throw AssertionError(message)
    }

    @JvmStatic
    private fun dbwRead(level: ServerLevel, pos: BlockPos): Int {
        try {
            val type = Class.forName("edn.stratodonut.drivebywire.wire.WireNetworkManager")
            val manager = type.getMethod("get", Level::class.java).invoke(null, level)
            type.getMethod("flushPendingGraphRebuild", Level::class.java).invoke(manager, level)
            return DbwAccess.read(level.getBlockEntity(pos)!!, "top")
        } catch (e: ReflectiveOperationException) {
            throw AssertionError(e)
        }
    }

    @JvmStatic
    fun run(level: ServerLevel): String {
        val source = BlockPos(72, 220, 8)
        val linkSource = source.east(4)
        level.setChunkForced(4, 0, true)
        level.setBlockAndUpdate(source, ModRegistry.Blocks.COMPUTER_NORMAL.get().defaultBlockState())
        var anchor = level.getBlockEntity(source)
        val bank = (anchor as ChannelHost).`teutonlib$channels`()
        bank.create("flight", "output", "")
        bank.set("flight", 11)
        bank.published("flight", true)
        anchor.setChanged()
        DbwAccess.set(anchor, "flight", 11)
        val targetId = UUID.randomUUID()
        (anchor as LinkIdentity).`teutonlib$linkTarget`(targetId)
        level.setBlockAndUpdate(linkSource.below(), Blocks.BARREL.defaultBlockState())
        level.setBlockAndUpdate(linkSource, PeripheralLinks.BLOCK.get().defaultBlockState())
        var link = level.getBlockEntity(linkSource) as PeripheralLinkBlockEntity?
        link!!.target(targetId)
        LinkRegistry.register(link)
        LinkRegistry.validate()
        check(link.connection != null, "stationary Sable fixture must connect")
        val linkId = link.identity()
        if (DbwAccess.available()) {
            try {
                val manager = Class.forName("edn.stratodonut.drivebywire.wire.WireNetworkManager")
                manager.getMethod(
                    "createConnection",
                    Level::class.java,
                    BlockPos::class.java,
                    BlockPos::class.java,
                    Direction::class.java,
                    String::class.java
                )
                    .invoke(null, level, source, linkSource.below(), Direction.UP, "flight")
            } catch (e: ReflectiveOperationException) {
                throw AssertionError(e)
            }
            check(dbwRead(level, linkSource.below()) == 11, "stationary DbW channel must deliver value")
        }
        val anchorShip = SubLevelAssemblyHelper.assembleBlocks(
            level,
            source,
            java.util.List.of<BlockPos>(source),
            BoundingBox3i(source, source)
        )
        val anchorPos = anchorShip.plot.centerBlock
        anchor = level.getBlockEntity(anchorPos)
        LinkRegistry.validate()
        check(targetId == (anchor as LinkIdentity).`teutonlib$linkTarget`(), "assembly must preserve target identity")
        check(
            (anchor as ChannelHost).`teutonlib$channels`()
                .get("flight").value == 11 && (anchor as ChannelHost).`teutonlib$channels`().get("flight").published,
            "assembly preserves channel definitions and values"
        )
        if (DbwAccess.available()) check(
            DbwAccess.connections(anchor, "flight") == 1,
            "assembly moves DbW output connections"
        )
        check(link.connection != null, "world to assembled target must connect")
        val linkShip = SubLevelAssemblyHelper.assembleBlocks(
            level, linkSource, java.util.List.of<BlockPos>(linkSource, linkSource.below()),
            BoundingBox3i(linkSource.below(), linkSource)
        )
        val linkPos = linkShip.plot.centerBlock
        link = level.getBlockEntity(linkPos) as PeripheralLinkBlockEntity?
        LinkRegistry.validate()
        check(
            linkId == link!!.identity() && targetId == link.target(),
            "assembly must preserve link identity and pairing"
        )
        check(link.connection != null, "separate sublevels must connect by world distance")
        if (DbwAccess.available()) check(
            dbwRead(level, linkPos.below()) == 11,
            "DbW signals cross separate assembled sublevels"
        )
        val savedPose = Pose3d(linkShip.logicalPose())
        linkShip.logicalPose().orientation().rotateY(Math.PI / 2)
        LinkRegistry.validate()
        check(link.connection != null, "rotated sublevel must remain connected")
        linkShip.logicalPose().position().add(30.0, 10.0, -20.0)
        LinkRegistry.validate()
        check(link.connection != null, "moving sublevel inside range must remain connected")
        if (DbwAccess.available()) check(
            dbwRead(level, linkPos.below()) == 11,
            "DbW signals survive motion and rotation"
        )
        linkShip.logicalPose().position().add(350.0, 0.0, 0.0)
        LinkRegistry.validate()
        check(link.connection == null && link.status == "range", "moving sublevel beyond range must disconnect")
        linkShip.logicalPose().set(savedPose)
        LinkRegistry.validate()
        check(link.connection != null, "returning sublevel must reconnect")
        // Disassembly uses the inverse block move with all original NBT intact.
        SubLevelAssemblyHelper.moveBlocks(
            level,
            AssemblyTransform(anchorPos, source, 0, Rotation.NONE, level),
            java.util.List.of<BlockPos>(anchorPos)
        )
        SubLevelAssemblyHelper.moveBlocks(
            level,
            AssemblyTransform(linkPos, linkSource, 0, Rotation.NONE, level),
            java.util.List.of<BlockPos>(linkPos, linkPos.below())
        )
        anchor = level.getBlockEntity(source)
        link = level.getBlockEntity(linkSource) as PeripheralLinkBlockEntity?
        LinkRegistry.validate()
        check(
            targetId == (anchor as LinkIdentity).`teutonlib$linkTarget`() && linkId == link!!.identity(),
            "disassembly must preserve both identities"
        )
        check(link!!.connection != null, "disassembled blocks must reconnect")
        check(
            (anchor as ChannelHost).`teutonlib$channels`().get("flight").value == 11,
            "disassembly preserves channel values"
        )
        if (DbwAccess.available()) check(dbwRead(level, linkSource.below()) == 11, "DbW signals survive disassembly")
        val container = SubLevelContainer.getContainer(level)
        container!!.removeSubLevel(anchorShip, SubLevelRemovalReason.REMOVED)
        container.removeSubLevel(linkShip, SubLevelRemovalReason.REMOVED)
        level.removeBlock(source, false)
        level.removeBlock(linkSource, false)
        level.removeBlock(linkSource.below(), false)
        level.setChunkForced(4, 0, false)
        return "PASS sable: assembly, disassembly, two sublevels, rotation, movement, range and reconnection"
    }
}
