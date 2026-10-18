@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package de.TeutonStudio.TeutonLib.peripheraltest

import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import com.mojang.authlib.GameProfile
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import dan200.computercraft.api.filesystem.WritableMount
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory
import dan200.computercraft.shared.peripheral.diskdrive.DiskDriveBlockEntity
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace
import de.TeutonStudio.TeutonLib.api.computer.SourceView
import de.TeutonStudio.TeutonLib.integration.computer.ChannelHost
import de.TeutonStudio.TeutonLib.integration.computer.ComputerIntegration
import de.TeutonStudio.TeutonLib.integration.computer.WorkspacePayloads
import de.TeutonStudio.TeutonLib.integration.peripheral.*
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkIdentity
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinkBlockEntity
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinks
import de.teutonstudio.ccaeroworks.compat.teutonlib.DeskComputerWorkspace
import de.teutonstudio.ccaeroworks.computer.ConsoleComputers
import de.teutonstudio.ccaeroworks.computer.channel.ChannelDescriptor
import de.teutonstudio.ccaeroworks.computer.channel.ChannelRegistry
import de.teutonstudio.ccaeroworks.computer.channel.UserChannelBindingView
import de.teutonstudio.ccaeroworks.computer.channel.UserChannelGroupView
import java.lang.AssertionError
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.function.Predicate
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.neoforge.common.util.FakePlayer

object ConsoleVerification {
    private val OWNER = BlockPos(40, 200, 8)
    private val ANCHOR: BlockPos = OWNER.east(2)
    private val MIDDLE: BlockPos = OWNER.east()
    private val LINK = BlockPos(44, 201, 10)
    private val SENSOR = BlockPos(44, 200, 12)
    private val DRIVE = BlockPos(46, 200, 12)
    private var computer: ServerComputer? = null
    private var deskState: BlockState? = null
    @JvmStatic
    private fun check(value: Boolean, message: String?) {
        if (!value) throw AssertionError(message)
    }

    @Throws(Exception::class)
    @JvmStatic
    private fun workspace(level: ServerLevel) {
        val owner = ConsoleComputers.get(level.getBlockEntity(OWNER))
        val backend = DeskComputerWorkspace
        check(owner != null && backend.owns(computer!!), "workspace owner identity")
        val player = FakePlayer(level, GameProfile(UUID.randomUUID(), "DeskViewer"))
        player.setPos(OWNER.x + .5, OWNER.y.toDouble(), OWNER.z + .5)
        player.containerMenu = ComputerMenuWithoutInventory(
            ModRegistry.Menus.COMPUTER.get(), 7, player.inventory,
            Predicate { p: Player? -> p!!.distanceToSqr(OWNER.center) < 64 }, computer
        )
        val handle = WorkspacePayloads::class.java.getDeclaredMethod(
            "handle",
            WorkspacePayloads.Request::class.java, ServerPlayer::class.java
        )
        handle.setAccessible(true)
        val session = UUID.randomUUID()
        val id: ResourceLocation = DeskComputerWorkspace.ID
        val registry = ChannelRegistry
        val create = ComputerWorkspace.Command(2, "add_wire", "", "", "", "workspace_test")
        handle.invoke(null, WorkspacePayloads.Request(8, session, id, create), player)
        check(registry.snapshot(owner!!).wires.isEmpty(), "stale workspace menu mutated channels")
        handle.invoke(null, WorkspacePayloads.Request(7, session, id, create), player)
        val wire =
            registry.snapshot(owner).wires.stream().filter { c: ChannelDescriptor -> c.name == "workspace_test" }
                .findFirst().orElseThrow()
        val selected = wire.id
        backend.execute(player, computer!!, ComputerWorkspace.Command(2, "add_group", "", "", "", "workspace_group"))
        val group =
            registry.snapshot(owner).groups.stream().filter { g: UserChannelGroupView -> g.name == "workspace_group" }
                .findFirst().orElseThrow()
        backend.execute(
            player,
            computer!!,
            ComputerWorkspace.Command(2, "bind", selected, group.id.toString(), selected, "engine")
        )
        check(
            registry.snapshot(owner).groups.stream().anyMatch { g: UserChannelGroupView? ->
                g!!.bindings.stream()
                    .anyMatch { b: UserChannelBindingView? -> b!!.alias == "engine" && b.targetId == selected }
            }, "workspace binding lost target identity"
        )
        backend.execute(
            player,
            computer!!,
            ComputerWorkspace.Command(2, "rename_binding", "binding:" + group.id + ":engine", "", "", "renamed")
        )
        check(
            owner.item().get<String>(de.teutonstudio.ccaeroworks.registry.CCDataComponents.CHANNEL_GROUPS.get())!!.contains("renamed"),
            "workspace group did not use existing item persistence"
        )
        val sources = backend.snapshot(player, computer!!, 1).encode()
        check(sources.toString().contains("peripheral:"), "workspace peripheral projection empty")
        check(
            !sources.toString().contains("teutonlib:peripherals"),
            "legacy source mixin duplicated explicit workspace projection"
        )
        val runtime = ComputerIntegration.runtime(computer!!)!!
        var rejected = false
        try {
            runtime.createChannel("second_bank", "output", "")
        } catch (expected: Exception) {
            rejected = true
        }
        check(
            rejected && (owner.host as ChannelHost).`teutonlib$channels`().entries().isEmpty(),
            "workspace created a second writable channel bank"
        )
        player.setPos(OWNER.x.toDouble(), (OWNER.y + 100).toDouble(), OWNER.z.toDouble())
        val remove = ComputerWorkspace.Command(2, "remove_wire", selected, "", "", "")
        handle.invoke(null, WorkspacePayloads.Request(7, session, id, remove), player)
        check(registry.snapshot(owner).wires.size == 1, "out of reach workspace mutation accepted")
        player.setPos(OWNER.x + .5, OWNER.y.toDouble(), OWNER.z + .5)
        val spectatorMenu = player.containerMenu
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR)
        player.containerMenu = spectatorMenu
        check(player.isSpectator && spectatorMenu.stillValid(player), "workspace spectator fixture invalid")
        handle.invoke(null, WorkspacePayloads.Request(7, session, id, remove), player)
        check(registry.snapshot(owner).wires.size == 1, "spectator workspace mutation accepted")
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
        player.containerMenu = spectatorMenu
        backend.execute(
            player,
            computer!!,
            ComputerWorkspace.Command(2, "remove_group", "group:" + group.id, "", "", "")
        )
        backend.execute(player, computer!!, remove)
        check(
            registry.snapshot(owner).wires.isEmpty() && registry.snapshot(owner).groups.isEmpty(),
            "workspace cleanup failed"
        )
        player.containerMenu.removed(player)
    }

    @Throws(Exception::class)
    @JvmStatic
    fun run(level: ServerLevel, action: String): String? {
        if (action == "workspace") {
            workspace(level)
            return "PASS workspace"
        }
        if (action == "normal" || action == "advanced") {
            level.setChunkForced(2, 0, true)
            for (x in 39..47) for (z in 7..12) for (y in 200..202) level.removeBlock(BlockPos(x, y, z), false)
            deskState =
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("aeroworks:control_desk")).defaultBlockState()
                    .setValue<Direction, Direction>(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
            for (x in 0..2) level.setBlockAndUpdate(OWNER.east(x), deskState)
            ConsoleMultiblockManager.invalidate(level)
            val anchor = level.getBlockEntity(ANCHOR) as ConsoleBlockEntity?
            val owner = level.getBlockEntity(OWNER) as ConsoleBlockEntity?
            val id = UUID.randomUUID()
            (anchor as LinkIdentity).`teutonlib$linkTarget`(id)
            for (pos in arrayOf<BlockPos>(LINK, LINK.east(2))) {
                level.setBlockAndUpdate(pos.below(), Blocks.BARREL.defaultBlockState())
                level.setBlockAndUpdate(pos, PeripheralLinks.BLOCK.get().defaultBlockState())
                val link = level.getBlockEntity(pos) as PeripheralLinkBlockEntity?
                link!!.target(id)
                LinkRegistry.register(link)
            }
            for (pos in arrayOf<BlockPos>(SENSOR, DRIVE)) {
                val device = if (pos == SENSOR) "simulated:gimbal_sensor" else "computercraft:disk_drive"
                level.setBlockAndUpdate(
                    pos,
                    BuiltInRegistries.BLOCK.get(ResourceLocation.parse(device)).defaultBlockState()
                )
                level.setBlockAndUpdate(pos.above(), PeripheralLinks.BLOCK.get().defaultBlockState())
                (level.getBlockEntity(pos.above()) as PeripheralLinkBlockEntity).target(id)
            }
            (level.getBlockEntity(DRIVE) as DiskDriveBlockEntity).setItem(
                0,
                ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:disk")))
            )
            val link = level.getBlockEntity(LINK) as PeripheralLinkBlockEntity?
            LinkRegistry.validate()
            check(link!!.connection == null && id == link.target(), "empty row must retain inactive pairing")
            val installed =
                ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:computer_" + action)))
            ConsoleComputers.install(owner!!, installed)
            ConsoleMultiblockManager.invalidate(level)
            LinkRegistry.validate()
            check(
                link.connection != null && link.connection!!.owner === owner,
                "computer at a different row member must own the link"
            )
            // Removing and reinstalling the computer retains the selected member's target identity.
            val removed = ConsoleComputers.remove(owner)
            ConsoleMultiblockManager.invalidate(level)
            LinkRegistry.validate()
            check(link.connection == null && id == link.target(), "computer removal must make pairing inactive")
            ConsoleComputers.install(owner, removed)
            ConsoleMultiblockManager.invalidate(level)
            LinkRegistry.validate()
            check(link.connection != null, "computer reinstallation must reconnect")
            computer = ConsoleComputers.get(owner)!!.createServerComputer()
            val name = link.connection!!.name()
            val program: String = """
                    local function result(text)
                        local f=assert(fs.open('link-result','w'));f.write(text);f.close()
                    end
                    local ok,why=pcall(function()
                        sleep(1)
                        local name='%s'
                        local standard=assert(peripheral.wrap(name))
                        local found=assert(peripherals.find('inventory'))
                        local desk=assert(found[name])
                        assert(standard.size()==27 and desk.size()==27)
                        local sensor=assert(peripheral.find('gimbal_sensor'))
                        local deskSensor=assert(peripherals.find('gimbal_sensor'))
                        local angles=assert(sensor.getAnglesRad())
                        local deskAngles=assert(deskSensor.getAnglesRad())
                        assert(#angles==2 and type(angles[1])=='number' and type(angles[2])=='number')
                        assert(angles[1]==deskAngles[1] and angles[2]==deskAngles[2])
                        local degrees=sensor.getAngles()
                        assert(math.abs(math.deg(angles[1])-degrees[1])<1e-12)
                        local drive=assert(peripheral.find('drive'))
                        local deskDrive=assert(peripherals.find('drive'))
                        assert(drive.getMountPath()==deskDrive.getMountPath())
                        assert(fs.isDir(drive.getMountPath()))
                        local info=desk.getPeripheralInfo()
                        assert(info.address==name and info.connectionType=='wireless')
                        assert(info.linkPosition.x==44 and info.targetPosition.y==200)
                        local count=0
                        for _,node in pairs(peripherals.getTree()) do
                            for key,child in pairs(node.peripherals) do
                                if child.connectionType=='wireless' then
                                    count=count+1;assert(key==child.address)
                                end
                            end
                        end
                        assert(count==4, 'wireless tree children overwritten')
                        result('READY')
                        os.pullEvent('test_split');sleep(.1)
                        assert(not peripheral.isPresent(name))
                        assert(not pcall(standard.size) and not pcall(desk.size))
                        result('DETACHED')
                        os.pullEvent('test_merged');sleep(.1)
                        assert(peripheral.wrap(name).size()==27)
                        assert(peripherals.find('inventory')[name].size()==27)
                        assert(not pcall(standard.size) and not pcall(desk.size))
                        result('PASS')
                    end)
                    if not ok then result('FAIL: '..tostring(why)) end
                    """.trimIndent().format(name)
            val mount: WritableMount = computer!!.createRootMount()!!
            if (mount.exists("link-result")) mount.delete("link-result")
            mount.openFile(
                "startup.lua",
                java.util.Set.of<OpenOption>(
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                )
            ).use { file ->
                file.write(StandardCharsets.UTF_8.encode(program))
            }
            computer!!.turnOn()
            return "STARTED console " + action
        }
        if (action == "result") {
            val mount: WritableMount = computer!!.createRootMount()!!
            if (!mount.exists("link-result")) return "WAITING"
            mount.openForRead("link-result").use { file ->
                val bytes = ByteBuffer.allocate(4096)
                file.read(bytes)
                bytes.flip()
                return StandardCharsets.UTF_8.decode(bytes).toString()
            }
        }
        if (action == "attachments") {
            val field = DiskDriveBlockEntity::class.java.getDeclaredField("computers")
            field.setAccessible(true)
            val attached = field.get(level.getBlockEntity(DRIVE)) as MutableMap<*, *>
            check(attached.size == 1, "standard and desk APIs must share exactly one physical attachment")
            val sources = ComputerIntegration.runtime(computer!!)!!.sourceViews()
            check(
                sources.stream()
                    .anyMatch { v: SourceView? -> v!!.name.startsWith("cc_aeroworks:link_") && v.types.contains("gimbal_sensor") },
                "workspace source directory includes linked sensor"
            )
            check(
                sources.stream().filter { v: SourceView? -> v!!.name.startsWith("cc_aeroworks:link_") }.count() >= 4,
                "workspace source directory includes all linked device bindings"
            )
            return "PASS one_attachment"
        }
        if (action == "reboot") {
            val mount: WritableMount = computer!!.createRootMount()!!
            if (mount.exists("link-result")) mount.delete("link-result")
            computer!!.reboot()
            return "REBOOT_REQUESTED"
        }
        if (action == "split") {
            level.removeBlock(MIDDLE, false)
            ConsoleMultiblockManager.invalidate(level)
            LinkRegistry.validate()
            computer!!.queueEvent("test_split")
            return "SPLIT_REQUESTED"
        }
        if (action == "merge") {
            level.setBlockAndUpdate(MIDDLE, deskState)
            ConsoleMultiblockManager.invalidate(level)
            LinkRegistry.validate()
            computer!!.queueEvent("test_merged")
            return "MERGE_REQUESTED"
        }
        throw IllegalArgumentException(action)
    }
}
