package de.TeutonStudio.TeutonLib.peripheraltest

import dan200.computercraft.api.network.Packet
import dan200.computercraft.api.network.PacketReceiver
import dan200.computercraft.api.network.PacketSender
import dan200.computercraft.api.filesystem.WritableMount
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.config.Config
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.peripheral.modem.ModemState
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork
import de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry
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
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import java.lang.AssertionError
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.Vec3

/** Uses Sable's actual block assembly/move implementation, not simulated coordinates.  */
object SableVerification {
    private const val GPS_RESULT = "sable-gps-result"
    private val GPS_RECEIVER = BlockPos(108, 188, 40)
    private val GPS_HOSTS = listOf(
        BlockPos(100, 180, 32),
        BlockPos(120, 180, 32),
        BlockPos(100, 200, 32),
        BlockPos(100, 180, 52),
    )
    private val GPS_MOVE = Vec3(30.0, 10.0, -20.0)
    private val GPS_DESTINATION = GPS_RECEIVER.offset(GPS_MOVE.x.toInt(), GPS_MOVE.y.toInt(), GPS_MOVE.z.toInt())
    private var gpsReceiver: ServerComputer? = null
    private var gpsComputers = mutableListOf<ServerComputer>()
    private var gpsShip: dev.ryanhcode.sable.sublevel.ServerSubLevel? = null
    private var gpsReceiverPlot: BlockPos? = null

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

    private class TestSender(
        private val testLevel: Level,
        private val testPosition: Vec3,
    ) : PacketSender {
        override fun getLevel(): Level = testLevel
        override fun getPosition(): Vec3 = testPosition
        override fun getSenderID(): String = "teutonlib-sable-test"
    }

    private class TestReceiver(
        private val testLevel: Level,
        private val testPosition: Vec3,
        private val testRange: Double,
        private val interdimensional: Boolean = false,
    ) : PacketReceiver {
        var distance: Double? = null
        var receivedAcrossDimension = false

        override fun getLevel(): Level = testLevel
        override fun getPosition(): Vec3 = testPosition
        override fun getRange(): Double = testRange
        override fun isInterdimensional(): Boolean = interdimensional

        override fun receiveSameDimension(packet: Packet, distance: Double) {
            this.distance = distance
        }

        override fun receiveDifferentDimension(packet: Packet) {
            receivedAcrossDimension = true
        }
    }

    /** Exercise CC:Tweaked's real WirelessNetwork so Sable's runtime mixin is part of the assertion. */
    private fun wirelessDistance(
        senderLevel: Level,
        senderPosition: Vec3,
        receiverLevel: Level,
        receiverPosition: Vec3,
        range: Double,
        interdimensional: Boolean = false,
    ): TestReceiver {
        val network = WirelessNetwork()
        val receiver = TestReceiver(receiverLevel, receiverPosition, range, interdimensional)
        network.addReceiver(receiver)
        network.transmitSameDimension(Packet(473, 474, "sable", TestSender(senderLevel, senderPosition)), range)
        return receiver
    }

    private fun checkWirelessDistance(
        senderLevel: Level,
        senderPosition: Vec3,
        receiverLevel: Level,
        receiverPosition: Vec3,
        message: String,
    ) {
        val expected = SubLevelGeometry.distance(receiverLevel, receiverPosition, senderPosition)
        val actual = wirelessDistance(
            senderLevel, senderPosition, receiverLevel, receiverPosition,
            expected + 1.0,
        ).distance
        check(actual != null, "$message must deliver")
        check(kotlin.math.abs(actual!! - expected) < 1.0e-6, "$message reports world-space distance")
    }

    private fun expectedNormalModemRange(level: Level, worldPosition: Vec3): Double {
        var base = Config.modemRange.toDouble()
        var high = Config.modemHighAltitudeRange.toDouble()
        if (level.isRaining && level.isThundering) {
            base = Config.modemRangeDuringStorm.toDouble()
            high = Config.modemHighAltitudeRangeDuringStorm.toDouble()
        }
        return if (worldPosition.y > 96.0 && high > base) {
            base + (worldPosition.y - 96.0) * (high - base) / (level.maxBuildHeight - 1.0 - 96.0)
        } else base
    }

    private fun writeStartup(computer: ServerComputer, program: String, resultFile: String? = null) {
        val mount: WritableMount = computer.createRootMount()!!
        if (resultFile != null && mount.exists(resultFile)) mount.delete(resultFile)
        mount.openFile(
            "startup.lua",
            setOf<OpenOption>(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
        ).use { it.write(StandardCharsets.UTF_8.encode(program)) }
        computer.turnOn()
    }

    private fun createGpsComputer(level: ServerLevel, pos: BlockPos, program: String, resultFile: String? = null): ServerComputer {
        level.setChunkForced(pos.x shr 4, pos.z shr 4, true)
        level.setBlockAndUpdate(pos, ModRegistry.Blocks.COMPUTER_NORMAL.get().defaultBlockState())
        level.setBlockAndUpdate(
            pos.west(),
            ModRegistry.Blocks.WIRELESS_MODEM_NORMAL.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST),
        )
        val host = level.getBlockEntity(pos) as ComputerBlockEntity
        host.neighborChanged(pos.west())
        host.updateInputsImmediately()
        val computer = host.createServerComputer()
        writeStartup(computer, program, resultFile)
        return computer
    }

    private fun gpsHostProgram(pos: BlockPos): String = """
        local modem=assert(peripheral.find('modem'),'missing modem')
        rednet.open(peripheral.getName(modem))
        shell.run('gps','host',${pos.x},${pos.y},${pos.z})
    """.trimIndent()

    private fun gpsReceiverProgram(): String = """
        local function result(value)
          local f=assert(fs.open('$GPS_RESULT','w')); f.write(value); f.close()
        end
        local function locate(label,ex,ey,ez)
          for attempt=1,20 do
            local x,y,z=gps.locate(2,false)
            if x and math.abs(x-ex)<0.15 and math.abs(y-ey)<0.15 and math.abs(z-ez)<0.15 then
              result(label..' '..x..' '..y..' '..z); return
            end
            sleep(0.1)
          end
          local x,y,z=gps.locate(2,false)
          result('FAIL '..label..' expected '..ex..','..ey..','..ez..' got '..tostring(x)..','..tostring(y)..','..tostring(z))
        end
        local modem=assert(peripheral.find('modem'),'missing modem')
        rednet.open(peripheral.getName(modem))
        locate('INITIAL_PASS',${GPS_RECEIVER.x},${GPS_RECEIVER.y},${GPS_RECEIVER.z})
        while true do
          local _,phase=os.pullEvent('sable_gps_phase')
          if phase=='moved' then
            locate('PASS',${GPS_RECEIVER.x + GPS_MOVE.x},${GPS_RECEIVER.y + GPS_MOVE.y},${GPS_RECEIVER.z + GPS_MOVE.z})
          end
        end
    """.trimIndent()

    private fun gpsResult(): String {
        val computer = gpsReceiver ?: return "NOT_STARTED"
        val mount = computer.createRootMount()!!
        if (!mount.exists(GPS_RESULT)) return "WAITING"
        mount.openForRead(GPS_RESULT).use { file ->
            val buffer = ByteBuffer.allocate(4096)
            file.read(buffer)
            buffer.flip()
            return StandardCharsets.UTF_8.decode(buffer).toString()
        }
    }

    private fun gpsPlatform(level: ServerLevel, center: BlockPos, place: Boolean) {
        for (x in -2..2) for (z in -2..2) {
            val floor = center.offset(x, -1, z)
            if (place) level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState())
            else level.removeBlock(floor, false)
        }
    }

    private fun cleanupGps(level: ServerLevel) {
        gpsComputers.forEach { it.shutdown() }
        gpsComputers.clear()
        gpsReceiver?.shutdown()
        gpsReceiver = null
        gpsShip?.let { SubLevelContainer.getContainer(level)?.removeSubLevel(it, SubLevelRemovalReason.REMOVED) }
        gpsShip = null
        gpsReceiverPlot = null
        GPS_HOSTS.forEach { pos ->
            level.removeBlock(pos, false)
            level.removeBlock(pos.west(), false)
            level.setChunkForced(pos.x shr 4, pos.z shr 4, false)
        }
        level.removeBlock(GPS_RECEIVER, false)
        level.removeBlock(GPS_RECEIVER.west(), false)
        gpsPlatform(level, GPS_RECEIVER, false)
        gpsPlatform(level, GPS_DESTINATION, false)
        level.setChunkForced(GPS_RECEIVER.x shr 4, GPS_RECEIVER.z shr 4, false)
        level.setChunkForced(GPS_DESTINATION.x shr 4, GPS_DESTINATION.z shr 4, false)
        level.server.getLevel(Level.NETHER)?.let { nether ->
            nether.removeBlock(GPS_HOSTS.first(), false)
            nether.removeBlock(GPS_HOSTS.first().west(), false)
            nether.setChunkForced(GPS_HOSTS.first().x shr 4, GPS_HOSTS.first().z shr 4, false)
        }
    }

    @JvmStatic
    fun runGps(level: ServerLevel, action: String): String {
        if (action == "start") {
            cleanupGps(level)
            GPS_HOSTS.forEach { pos -> gpsComputers += createGpsComputer(level, pos, gpsHostProgram(pos)) }
            level.setChunkForced(GPS_RECEIVER.x shr 4, GPS_RECEIVER.z shr 4, true)
            level.setChunkForced(GPS_DESTINATION.x shr 4, GPS_DESTINATION.z shr 4, true)
            gpsPlatform(level, GPS_RECEIVER, true)
            gpsPlatform(level, GPS_DESTINATION, true)
            level.setBlockAndUpdate(GPS_RECEIVER, ModRegistry.Blocks.COMPUTER_NORMAL.get().defaultBlockState())
            level.setBlockAndUpdate(
                GPS_RECEIVER.west(),
                ModRegistry.Blocks.WIRELESS_MODEM_NORMAL.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST),
            )
            val ship = SubLevelAssemblyHelper.assembleBlocks(
                level,
                GPS_RECEIVER,
                listOf(GPS_RECEIVER, GPS_RECEIVER.west()),
                BoundingBox3i(GPS_RECEIVER.west(), GPS_RECEIVER),
            )
            gpsShip = ship
            val plot = ship.plot.centerBlock
            gpsReceiverPlot = plot
            val host = level.getBlockEntity(plot) as ComputerBlockEntity
            host.neighborChanged(plot.west())
            host.updateInputsImmediately()
            gpsReceiver = host.createServerComputer().also { writeStartup(it, gpsReceiverProgram(), GPS_RESULT) }

            // A deliberately bogus real host in another dimension must never participate.
            level.server.getLevel(Level.NETHER)?.let { nether ->
                val pos = GPS_HOSTS.first()
                gpsComputers += createGpsComputer(nether, pos, gpsHostProgram(BlockPos(999, 999, 999)))
            }
            return "STARTED sable_gps"
        }
        if (action == "result") return gpsResult()
        if (action == "move") {
            check(gpsResult().startsWith("INITIAL_PASS"), "GPS fixture must locate before movement")
            val ship = gpsShip!!
            val target = Pose3d(ship.logicalPose())
            target.position().add(GPS_MOVE.x, GPS_MOVE.y, GPS_MOVE.z)
            val handle = RigidBodyHandle.of(ship)
            check(handle != null && handle.isValid, "GPS fixture rigid body must be ready before movement")
            ship.logicalPose().set(target)
            handle!!.teleport(target.position(), target.orientation())
            SubLevelPhysicsSystem.require(level).pipeline.resetVelocity(ship)
            gpsReceiver!!.queueEvent("sable_gps_phase", arrayOf<Any>("moved"))
            return "MOVED sable_gps"
        }
        if (action == "cleanup") {
            check(gpsResult().startsWith("PASS"), "GPS fixture must locate after movement")
            cleanupGps(level)
            return "PASS sable_gps"
        }
        throw IllegalArgumentException("Unknown Sable GPS action $action")
    }

    @JvmStatic
    fun run(level: ServerLevel): String {
        val source = BlockPos(72, 220, 8)
        val linkSource = source.east(4)
        level.setChunkForced(4, 0, true)
        level.setBlockAndUpdate(source, ModRegistry.Blocks.COMPUTER_NORMAL.get().defaultBlockState())
        var anchor = level.getBlockEntity(source)
        checkWirelessDistance(
            level, source.center, level, linkSource.center,
            "stationary world-to-world wireless network",
        )
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
        checkWirelessDistance(
            anchor!!.level!!, anchorPos.center, level, linkSource.center,
            "world-to-sublevel wireless network",
        )
        val normalModem = object : WirelessModemPeripheral(ModemState(), false) {
            override fun getLevel(): Level = anchor!!.level!!
            override fun getPosition(): Vec3 = anchorPos.center
            override fun getTarget(): Any = anchor!!
            override fun equals(other: dan200.computercraft.api.peripheral.IPeripheral?): Boolean = other === this
        }
        val projectedAnchor = SubLevelGeometry.projectToWorld(anchor!!.level!!, anchorPos.center)
        check(
            kotlin.math.abs(normalModem.range - expectedNormalModemRange(level, projectedAnchor)) < 1.0e-6,
            "normal wireless modem range must use projected world Y",
        )
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
        checkWirelessDistance(
            anchor!!.level!!, anchorPos.center, link!!.level!!, linkPos.center,
            "wireless network between two sublevels",
        )
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
        checkWirelessDistance(
            anchor!!.level!!, anchorPos.center, link!!.level!!, linkPos.center,
            "wireless network after sublevel rotation",
        )
        LinkRegistry.validate()
        check(link.connection != null, "rotated sublevel must remain connected")
        linkShip.logicalPose().position().add(30.0, 10.0, -20.0)
        checkWirelessDistance(
            anchor!!.level!!, anchorPos.center, link!!.level!!, linkPos.center,
            "wireless network after sublevel movement",
        )
        LinkRegistry.validate()
        check(link.connection != null, "moving sublevel inside range must remain connected")
        if (DbwAccess.available()) check(
            dbwRead(level, linkPos.below()) == 11,
            "DbW signals survive motion and rotation"
        )
        linkShip.logicalPose().position().add(350.0, 0.0, 0.0)
        check(
            wirelessDistance(
                anchor!!.level!!, anchorPos.center, link!!.level!!, linkPos.center, 256.0,
            ).distance == null,
            "wireless network must reject a moved sublevel outside range",
        )
        LinkRegistry.validate()
        check(link.connection == null && link.status == "range", "moving sublevel beyond range must disconnect")
        linkShip.logicalPose().set(savedPose)
        checkWirelessDistance(
            anchor!!.level!!, anchorPos.center, link!!.level!!, linkPos.center,
            "wireless network after returning sublevel",
        )
        LinkRegistry.validate()
        check(link.connection != null, "returning sublevel must reconnect")
        level.server.getLevel(Level.NETHER)?.let { otherLevel ->
            val isolated = wirelessDistance(
                level, source.center, otherLevel, source.center, 1_000_000.0,
            )
            check(
                isolated.distance == null && !isolated.receivedAcrossDimension,
                "same-dimension wireless transmission must remain dimension-isolated",
            )
        }
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
        return "PASS sable: wireless world distance, world Y, dimensions, assembly, disassembly, two sublevels, rotation, movement, range and reconnection"
    }
}
