package de.TeutonStudio.TeutonLib.peripheraltest

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dan200.computercraft.api.filesystem.WritableMount
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.peripheral.diskdrive.DiskDriveBlockEntity
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemFullBlockEntity
import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.peripheral.*
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkIdentity
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinkBlockEntity
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinks
import java.lang.AssertionError
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import java.util.ArrayList
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler

/** Real CC/CraftOS verification. Loaded only by the explicitly enabled development source set.  */
@Mod("teutonlib_peripheral_test")
class PeripheralVerification {
    init {
        NeoForge.EVENT_BUS.addListener<RegisterCommandsEvent>(Consumer { event: RegisterCommandsEvent? ->
            this.commands(
                event!!
            )
        })
    }

    private fun commands(event: RegisterCommandsEvent) {
        event.dispatcher
            .register(
                Commands.literal("teutonlib_link_test")
                    .requires(Predicate { source: CommandSourceStack? -> source!!.hasPermission(4) })
                    .then(
                        Commands.argument<String>("action", StringArgumentType.word())
                            .executes(Command { ctx: CommandContext<CommandSourceStack> ->
                                try {
                                    val result: String =
                                        run(ctx!!.source!!.level, StringArgumentType.getString(ctx, "action"))
                                    ctx.source!!.sendSuccess(Supplier { Component.literal(result) }, false)
                                    return@Command 1
                                } catch (failure: Throwable) {
                                    TeutonLib.Companion.LOGGER.error("Peripheral verification failed", failure)
                                    ctx!!.source!!.sendFailure(Component.literal("FAIL: " + failure))
                                    return@Command 0
                                }
                            })
                    )
            )
    }

    companion object {
        private val COMPUTER = BlockPos(8, 200, 8)
        private val FIRST = BlockPos(12, 201, 8)
        private val SECOND = BlockPos(14, 201, 8)
        private var computer: ServerComputer? = null
        private var savedLink: CompoundTag? = null
        private var firstName: String? = null
        private val CHUNK_TARGET = BlockPos(16008, 200, 8)
        private val CHUNK_LINK: BlockPos = CHUNK_TARGET.east(96)
        private var chunkName: String? = null
        private val checks: MutableList<String> = ArrayList<String>()

        @JvmStatic
        private fun require(condition: Boolean, description: String) {
            if (!condition) throw AssertionError(description)
            checks.add(description)
        }

        @JvmStatic
        private fun block(id: String): Block {
            return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id))
        }

        @JvmStatic
        private fun link(level: ServerLevel, pos: BlockPos, target: UUID?): PeripheralLinkBlockEntity {
            level.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState())
            level.setBlockAndUpdate(pos, PeripheralLinks.BLOCK.get().defaultBlockState())
            val link = level.getBlockEntity(pos) as PeripheralLinkBlockEntity
            link.target(target)
            LinkRegistry.register(link)
            return link
        }

        @Throws(Exception::class)
        @JvmStatic
        private fun run(level: ServerLevel, action: String): String {
            if (action.startsWith("channels_")) return ChannelsVerification.run(level, action.substring(9))!!
            if (action.startsWith("console_")) return ConsoleVerification.run(level, action.substring(8))!!
            if (action == "sable") return SableVerification.run(level)
            if (action == "chunk_prepare") {
                level.setChunkForced(CHUNK_TARGET.x shr 4, 0, true)
                level.setChunkForced(CHUNK_LINK.x shr 4, 0, true)
                level.setBlockAndUpdate(CHUNK_TARGET, block("computercraft:computer_normal").defaultBlockState())
                val target = level.getBlockEntity(CHUNK_TARGET)
                val id = UUID.randomUUID()
                (target as LinkIdentity).`teutonlib$linkTarget`(id)
                val link: PeripheralLinkBlockEntity = link(level, CHUNK_LINK, id)
                LinkRegistry.validate()
                require(link.connection != null, "chunk lifecycle fixture connected")
                chunkName = link.connection!!.name()
                level.setChunkForced(CHUNK_LINK.x shr 4, 0, false)
                return "STARTED chunk unload"
            }
            if (action == "chunk_unloaded") {
                if (level.hasChunkAt(CHUNK_LINK)) return "WAITING"
                require(
                    LinkRegistry.connections(level.getBlockEntity(CHUNK_TARGET)).isEmpty(),
                    "actual chunk unload detaches without loading it"
                )
                return "PASS chunk_unloaded"
            }
            if (action == "chunk_reload") {
                level.setChunkForced(CHUNK_LINK.x shr 4, 0, true)
                level.getChunkAt(CHUNK_LINK)
                return "STARTED chunk reload"
            }
            if (action == "chunk_reconnected") {
                if (LinkRegistry.connections(level.getBlockEntity(CHUNK_TARGET)).stream()
                        .noneMatch { connection: LinkRegistry.Connection? -> connection!!.name() == chunkName }
                ) return "WAITING"
                level.removeBlock(CHUNK_LINK, false)
                level.removeBlock(CHUNK_LINK.below(), false)
                level.removeBlock(CHUNK_TARGET, false)
                level.setChunkForced(CHUNK_TARGET.x shr 4, 0, false)
                level.setChunkForced(CHUNK_LINK.x shr 4, 0, false)
                return "PASS chunk_reconnected"
            }
            if (action == "normal" || action == "advanced") {
                checks.clear()
                level.setChunkForced(0, 0, true)
                level.removeBlock(COMPUTER, false)
                for (x in 8..15) for (y in 200..202) for (z in 8..15) level.removeBlock(BlockPos(x, y, z), false)
                level.setBlockAndUpdate(COMPUTER, block("computercraft:computer_" + action).defaultBlockState())
                val target = level.getBlockEntity(COMPUTER) as ComputerBlockEntity?
                val player = FakePlayerFactory.getMinecraft(level)
                player.setPos(COMPUTER.x + .5, COMPUTER.y.toDouble(), (COMPUTER.z + 2).toDouble())
                val item = ItemStack(PeripheralLinks.ITEM.get(), 3)
                player.setItemInHand(InteractionHand.MAIN_HAND, item)
                val context = UseOnContext(
                    player, InteractionHand.MAIN_HAND,
                    BlockHitResult(COMPUTER.center, Direction.UP, COMPUTER, false)
                )
                require(
                    player.gameMode.useItemOn(
                        player, level, item, InteractionHand.MAIN_HAND,
                        BlockHitResult(COMPUTER.center, Direction.UP, COMPUTER, false)
                    ).consumesAction(), "pairing succeeds through player interaction"
                )
                val id = (target as LinkIdentity).`teutonlib$linkTarget`()
                require(id != null, "target identity persisted on block")
                val clearItem = item.copy()
                player.setItemInHand(InteractionHand.MAIN_HAND, clearItem)
                player.setShiftKeyDown(true)
                clearItem.use(level, player, InteractionHand.MAIN_HAND)
                player.setShiftKeyDown(false)
                require(
                    !clearItem.getOrDefault<CustomData>(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY
                    ).copyTag().hasUUID("TeutonLibSelectedTarget"), "sneaking clears item selection"
                )
                player.setItemInHand(InteractionHand.MAIN_HAND, item)
                val selectedCopy = item.copy()
                // Exercise actual item placement against a generic inventory, then the runtime registry.
                level.setBlockAndUpdate(FIRST.below(), Blocks.CHEST.defaultBlockState())
                player.setPos(FIRST.x + .5, FIRST.y.toDouble(), (FIRST.z + 2).toDouble())
                require(
                    player.gameMode.useItemOn(
                        player, level, item, InteractionHand.MAIN_HAND,
                        BlockHitResult(FIRST.below().center, Direction.UP, FIRST.below(), false)
                    ).consumesAction(), "item placement through chest interaction succeeds"
                )
                var first = level.getBlockEntity(FIRST) as PeripheralLinkBlockEntity?
                require(first != null && id == first.target(), "placement retains selected target")
                require(
                    !item.getOrDefault<CustomData>(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY
                    ).copyTag().hasUUID("TeutonLibSelectedTarget"),
                    "successful placement clears remaining stack selection"
                )
                // Failure preserves the selected address; creative placement clears it without consuming the stack.
                player.setItemInHand(InteractionHand.MAIN_HAND, selectedCopy)
                require(
                    !player.gameMode.useItemOn(
                        player, level, selectedCopy, InteractionHand.MAIN_HAND,
                        BlockHitResult(FIRST.below().center, Direction.UP, FIRST.below(), false)
                    ).consumesAction(), "occupied placement fails"
                )
                require(
                    selectedCopy.getOrDefault<CustomData>(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY
                    ).copyTag().hasUUID("TeutonLibSelectedTarget"), "failed placement retains selection"
                )
                val singleItem = selectedCopy.copyWithCount(1)
                val creative: BlockPos = FIRST.south(2)
                level.setBlockAndUpdate(creative.below(), Blocks.CHEST.defaultBlockState())
                player.setPos(creative.x + .5, creative.y.toDouble(), (creative.z + 2).toDouble())
                player.setGameMode(GameType.CREATIVE)
                val countBefore = selectedCopy.count
                require(
                    player.gameMode.useItemOn(
                        player, level, selectedCopy, InteractionHand.MAIN_HAND,
                        BlockHitResult(creative.below().center, Direction.UP, creative.below(), false)
                    ).consumesAction(), "creative placement succeeds"
                )
                require(
                    (level.getBlockEntity(creative) as PeripheralLinkBlockEntity).target() == id,
                    "creative placed binding retained"
                )
                require(
                    selectedCopy.count == countBefore && !selectedCopy.getOrDefault<CustomData>(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY
                    ).copyTag().hasUUID("TeutonLibSelectedTarget"), "creative selection cleared without consumption"
                )
                level.removeBlock(creative, false)
                level.removeBlock(creative.below(), false)
                player.setGameMode(GameType.SURVIVAL)
                level.setBlockAndUpdate(creative.below(), Blocks.CHEST.defaultBlockState())
                player.setItemInHand(InteractionHand.MAIN_HAND, singleItem)
                require(
                    player.gameMode.useItemOn(
                        player, level, singleItem, InteractionHand.MAIN_HAND,
                        BlockHitResult(creative.below().center, Direction.UP, creative.below(), false)
                    ).consumesAction(), "single-item placement succeeds"
                )
                require(
                    singleItem.isEmpty() && (level.getBlockEntity(creative) as PeripheralLinkBlockEntity).target() == id,
                    "last item consumed after copying binding"
                )
                level.removeBlock(creative, false)
                level.removeBlock(creative.below(), false)
                player.setItemInHand(InteractionHand.MAIN_HAND, item)
                LinkRegistry.register(first)
                link(level, SECOND, id)
                LinkRegistry.validate()
                require(LinkRegistry.connections(target).size == 2, "multiple links share one target")
                val limit = ServerConfig.PERIPHERAL_LINK_LIMIT.get()
                try {
                    ServerConfig.PERIPHERAL_LINK_LIMIT.set(1)
                    LinkRegistry.validate()
                    require(LinkRegistry.connections(target).size == 1, "configured network limit enforced")
                } finally {
                    ServerConfig.PERIPHERAL_LINK_LIMIT.set(limit)
                    LinkRegistry.validate()
                }
                require(
                    first!!.connection!!.peripheral.type == "inventory"
                            || first.connection!!.peripheral.additionalTypes.contains("inventory"),
                    "generic inventory resolved through CC"
                )
                firstName = first.connection!!.name()
                target.persistentData.putString("ExpectedLinkName", firstName)
                target.setChanged()
                savedLink = first.saveWithFullMetadata(level.registryAccess())
                val original = first.identity()
                level.removeBlock(FIRST, false)
                level.setBlockAndUpdate(FIRST, PeripheralLinks.BLOCK.get().defaultBlockState())
                first = level.getBlockEntity(FIRST) as PeripheralLinkBlockEntity?
                first!!.loadWithComponents(savedLink, level.registryAccess())
                LinkRegistry.register(first)
                LinkRegistry.validate()
                require(
                    first.identity() == original && first.connection!!.name() == firstName,
                    "save/load preserves peripheral name"
                )
                // Boundary fixture: exact 256 world blocks, and one block beyond it.
                val boundary: BlockPos = COMPUTER.offset(256, 0, 0)
                level.setChunkForced(boundary.x shr 4, boundary.z shr 4, true)
                val edge: PeripheralLinkBlockEntity = link(level, boundary, id)
                LinkRegistry.validate()
                require(edge.connection != null, "inclusive range boundary")
                level.removeBlock(boundary, false)
                val outside: PeripheralLinkBlockEntity = link(level, boundary.east(), id)
                LinkRegistry.validate()
                require(outside.connection == null && outside.status == "range", "beyond range rejected")
                level.removeBlock(boundary.east(), false)
                level.setChunkForced(boundary.x shr 4, boundary.z shr 4, false)
                // A real block in another dimension cannot bind to the overworld anchor.
                val nether = level.server.getLevel(Level.NETHER)
                nether!!.setChunkForced(0, 0, true)
                val wrong: PeripheralLinkBlockEntity = Companion.link(nether, FIRST, id)
                LinkRegistry.validate()
                require(wrong.connection == null && wrong.status == "dimension", "different dimension rejected")
                nether.removeBlock(FIRST, false)
                nether.setChunkForced(0, 0, false)
                LinkRegistry.remove(first)
                LinkRegistry.validate()
                require(first.connection == null, "unload disconnects")
                LinkRegistry.register(first)
                LinkRegistry.validate()
                require(first.connection != null, "reload reconnects")
                level.setBlockAndUpdate(FIRST.below(), Blocks.STONE.defaultBlockState())
                LinkRegistry.validate()
                require(
                    first.connection == null && first.status == "no_peripheral",
                    "missing peripheral disconnects"
                )
                level.setBlockAndUpdate(FIRST.below(), Blocks.CHEST.defaultBlockState())
                LinkRegistry.validate()
                require(first.connection != null, "peripheral replacement reconnects")
                (level.getBlockEntity(FIRST.below()) as ChestBlockEntity).setItem(0, ItemStack(Items.DIAMOND, 16))
                level.setBlockAndUpdate(COMPUTER.east(), Blocks.BARREL.defaultBlockState())
                level.setBlockAndUpdate(COMPUTER.south(), block("computercraft:wired_modem_full").defaultBlockState())
                level.setBlockAndUpdate(COMPUTER.south().east(), Blocks.BARREL.defaultBlockState())
                (level.getBlockEntity(COMPUTER.south()) as WiredModemFullBlockEntity).use(player)
                // A disk drive connected through a third link mounts in the actual computer filesystem.
                val drive = BlockPos(10, 200, 12)
                level.setBlockAndUpdate(drive, block("computercraft:disk_drive").defaultBlockState())
                val diskDrive = level.getBlockEntity(drive) as DiskDriveBlockEntity?
                diskDrive!!.setItem(
                    0,
                    ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:disk")))
                )
                val driveLink: PeripheralLinkBlockEntity = link(level, drive.above(), id)
                level.setBlockAndUpdate(drive, block("computercraft:disk_drive").defaultBlockState())
                (level.getBlockEntity(drive) as DiskDriveBlockEntity).setItem(
                    0,
                    ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:disk")))
                )
                LinkRegistry.validate()
                val secondName = (level.getBlockEntity(SECOND) as PeripheralLinkBlockEntity).connection!!.name()
                val driveName = driveLink.connection!!.name()
                val monitorPos = BlockPos(10, 200, 14)
                val monitorLink: PeripheralLinkBlockEntity = link(level, monitorPos.above(), id)
                level.setBlockAndUpdate(monitorPos, block("computercraft:monitor_advanced").defaultBlockState())
                val tankA = BlockPos(12, 200, 14)
                val tankB = BlockPos(14, 200, 14)
                val tankLinkA: PeripheralLinkBlockEntity = link(level, tankA.above(), id)
                val tankLinkB: PeripheralLinkBlockEntity = link(level, tankB.above(), id)
                level.setBlockAndUpdate(tankA, block("create:fluid_tank").defaultBlockState())
                level.setBlockAndUpdate(tankB, block("create:fluid_tank").defaultBlockState())
                val fluid = level.getCapability<IFluidHandler, Direction?>(
                    Capabilities.FluidHandler.BLOCK,
                    tankA,
                    Direction.UP
                )
                require(
                    fluid != null && fluid.fill(
                        FluidStack(Fluids.WATER, 4000),
                        IFluidHandler.FluidAction.EXECUTE
                    ) == 4000, "fluid fixture filled"
                )
                LinkRegistry.validate()
                computer = target.createServerComputer()
                val program: String = """
                    local function result(text)
                        local f=assert(fs.open('link-result','w')); f.write(text); f.close()
                    end
                    local ok, failure = pcall(function()
                        sleep(1)
                        local a, b, drive = '%s', '%s', '%s'
                        assert(peripheral.isPresent(a))
                        assert(peripheral.hasType(a, 'inventory'))
                        local old = assert(peripheral.wrap(a))
                        assert(peripheral.getName(old) == a and peripheral.hasType(old,'inventory'))
                        assert(old.size() == 27)
                        local names = peripheral.getNames()
                        assert(#names >= 4)
                        local count=0
                        for _,p in ipairs({peripheral.find('inventory')}) do count=count+1 end
                        assert(count >= 3)
                        local localName, wiredName
                        local sides={}
                        for _,side in ipairs(rs.getSides()) do sides[side]=true end
                        for _, name in ipairs(names) do
                            if not name:find('cc_aeroworks:link_',1,true) and peripheral.hasType(name,'inventory') then
                                if sides[name] then localName=name else wiredName=name end
                            end
                        end
                        assert(localName, 'local barrel absent')
                        assert(wiredName, 'wired barrel absent')
                        assert(peripheral.call(a,'pushItems',b,1,5) == 5)
                        assert(peripheral.call(b,'pushItems',localName,1,2) == 2)
                        assert(peripheral.call(localName,'pushItems',a,1,1) == 1)
                        assert(peripheral.call(a,'pushItems',wiredName,1,3) == 3)
                        assert(peripheral.call(wiredName,'pushItems',b,1,1) == 1)
                        local monitor=assert(peripheral.wrap('%s'))
                        monitor.setCursorPos(2,3)
                        local x,y=monitor.getCursorPos();assert(x==2 and y==3)
                        monitor.setTextScale(.5)
                        local timer=os.startTimer(3)
                        while true do
                            local event,name=os.pullEvent()
                            if event=='monitor_resize' then assert(name=='%s');break end
                            if event=='timer' and name==timer then error('monitor event missing') end
                        end
                        local tankA,tankB=assert(peripheral.wrap('%s')),assert(peripheral.wrap('%s'))
                        assert(tankA.pushFluid('%s',1250,'minecraft:water')==1250)
                        assert(tankB.pullFluid('%s',250,'minecraft:water')==250)
                        assert(tankB.tanks()[1].amount==1500)
                        local path = peripheral.call(drive,'getMountPath')
                        assert(path and fs.isDir(path), 'drive mount absent')
                        result('READY')
                        os.pullEvent('test_detached')
                        sleep(0.1)
                        assert(not peripheral.isPresent(a))
                        assert(not pcall(old.size), 'old handle survived detach')
                        result('DETACHED')
                        os.pullEvent('test_reconnected')
                        sleep(0.1)
                        assert(peripheral.isPresent(a))
                        assert(peripheral.wrap(a).size() == 27)
                        assert(not pcall(old.size), 'old handle revived')
                        result('PASS')
                    end)
                    if not ok then result('FAIL: '..tostring(failure)) end
                    """.trimIndent().format(
                    firstName,
                    secondName,
                    driveName,
                    monitorLink.connection!!.name(),
                    monitorLink.connection!!.name(),
                    tankLinkA.connection!!.name(),
                    tankLinkB.connection!!.name(),
                    tankLinkB.connection!!.name(),
                    tankLinkA.connection!!.name()
                )
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
                return "STARTED " + action + " checks=" + checks.size
            }
            if (action == "result") {
                val mount: WritableMount = computer!!.createRootMount()!!
                if (!mount.exists("link-result")) return "WAITING"
                mount.openForRead("link-result").use { file ->
                    val buffer = ByteBuffer.allocate(4096)
                    file.read(buffer)
                    buffer.flip()
                    return StandardCharsets.UTF_8.decode(buffer).toString()
                }
            }
            if (action == "detach") {
                level.removeBlock(FIRST, false)
                LinkRegistry.validate()
                computer!!.queueEvent("test_detached")
                return "DETACH_REQUESTED"
            }
            if (action == "reconnect") {
                level.setBlockAndUpdate(FIRST, PeripheralLinks.BLOCK.get().defaultBlockState())
                val first = level.getBlockEntity(FIRST) as PeripheralLinkBlockEntity?
                first!!.loadWithComponents(savedLink, level.registryAccess())
                LinkRegistry.register(first)
                LinkRegistry.validate()
                computer!!.queueEvent("test_reconnected")
                return "RECONNECT_REQUESTED"
            }
            if (action == "conflict") {
                val target = level.getBlockEntity(COMPUTER)
                val other: BlockPos = COMPUTER.south(4)
                level.setBlockAndUpdate(other, target!!.blockState)
                val copy = level.getBlockEntity(other)
                copy!!.loadWithComponents(target.saveWithFullMetadata(level.registryAccess()), level.registryAccess())
                LinkRegistry.registerAnchor(copy)
                LinkRegistry.validate()
                require(LinkRegistry.connections(target).isEmpty(), "copied UUID disables target")
                level.removeBlock(other, false)
                LinkRegistry.validate()
                require(LinkRegistry.connections(target).isEmpty(), "conflict stays disabled after removal")
                return "PASS conflict"
            }
            if (action == "persistence") {
                level.getChunkAt(COMPUTER)
                val target = level.getBlockEntity(COMPUTER)
                val first = level.getBlockEntity(FIRST) as PeripheralLinkBlockEntity?
                LinkRegistry.registerAnchor(target!!)
                LinkRegistry.register(first)
                LinkRegistry.validate()
                require(
                    ("cc_aeroworks:link_" + first!!.identity()) == target!!.persistentData
                        .getString("ExpectedLinkName"), "server restart preserves link name"
                )
                require(
                    LinkRegistry.connections(target).isEmpty(),
                    "UUID conflict remains disabled after server restart"
                )
                return "PASS persistence"
            }
            throw IllegalArgumentException("Unknown action " + action)
        }
    }
}
