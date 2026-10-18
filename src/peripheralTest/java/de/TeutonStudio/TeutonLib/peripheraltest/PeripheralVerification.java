package de.TeutonStudio.TeutonLib.peripheraltest;

import com.mojang.brigadier.arguments.StringArgumentType;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.peripheral.diskdrive.DiskDriveBlockEntity;
import de.TeutonStudio.TeutonLib.TeutonLib;
import de.TeutonStudio.TeutonLib.integration.peripheral.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.*;

/** Real CC/CraftOS verification. Loaded only by the explicitly enabled development source set. */
@Mod("teutonlib_peripheral_test")
public final class PeripheralVerification {
    private static final BlockPos COMPUTER = new BlockPos(8, 200, 8);
    private static final BlockPos FIRST = new BlockPos(12, 201, 8);
    private static final BlockPos SECOND = new BlockPos(14, 201, 8);
    private static ServerComputer computer;
    private static CompoundTag savedLink;
    private static String firstName;
    private static final BlockPos CHUNK_TARGET = new BlockPos(16008, 200, 8), CHUNK_LINK = CHUNK_TARGET.east(96);
    private static String chunkName;
    private static final List<String> checks = new ArrayList<>();

    public PeripheralVerification() { NeoForge.EVENT_BUS.addListener(this::commands); }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("teutonlib_link_test").requires(source -> source.hasPermission(4))
                .then(Commands.argument("action", StringArgumentType.word()).executes(ctx -> {
                    try {
                        String result = run(ctx.getSource().getLevel(), StringArgumentType.getString(ctx, "action"));
                        ctx.getSource().sendSuccess(() -> Component.literal(result), false); return 1;
                    } catch (Throwable failure) {
                        TeutonLib.LOGGER.error("Peripheral verification failed", failure);
                        ctx.getSource().sendFailure(Component.literal("FAIL: " + failure)); return 0;
                    }
                })));
    }
    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }
    private static Block block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)); }
    private static PeripheralLinkBlockEntity link(ServerLevel level, BlockPos pos, UUID target) {
        level.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(pos, PeripheralLinks.BLOCK.get().defaultBlockState());
        var link = (PeripheralLinkBlockEntity) level.getBlockEntity(pos);
        link.target(target); LinkRegistry.register(link); return link;
    }
    private static String run(ServerLevel level, String action) throws Exception {
        if (action.startsWith("channels_")) return ChannelsVerification.run(level, action.substring(9));
        if (action.startsWith("console_")) return ConsoleVerification.run(level, action.substring(8));
        if (action.equals("sable")) return SableVerification.run(level);
        if (action.equals("chunk_prepare")) {
            level.setChunkForced(CHUNK_TARGET.getX() >> 4, 0, true);
            level.setChunkForced(CHUNK_LINK.getX() >> 4, 0, true);
            level.setBlockAndUpdate(CHUNK_TARGET, block("computercraft:computer_normal").defaultBlockState());
            var target = level.getBlockEntity(CHUNK_TARGET);
            UUID id = UUID.randomUUID(); ((LinkIdentity) target).teutonlib$linkTarget(id);
            var link = link(level, CHUNK_LINK, id); LinkRegistry.validate();
            require(link.connection != null, "chunk lifecycle fixture connected"); chunkName = link.connection.name();
            level.setChunkForced(CHUNK_LINK.getX() >> 4, 0, false);
            return "STARTED chunk unload";
        }
        if (action.equals("chunk_unloaded")) {
            if (level.hasChunkAt(CHUNK_LINK)) return "WAITING";
            require(LinkRegistry.connections(level.getBlockEntity(CHUNK_TARGET)).isEmpty(), "actual chunk unload detaches without loading it");
            return "PASS chunk_unloaded";
        }
        if (action.equals("chunk_reload")) {
            level.setChunkForced(CHUNK_LINK.getX() >> 4, 0, true); level.getChunkAt(CHUNK_LINK);
            return "STARTED chunk reload";
        }
        if (action.equals("chunk_reconnected")) {
            if (LinkRegistry.connections(level.getBlockEntity(CHUNK_TARGET)).stream().noneMatch(connection -> connection.name().equals(chunkName))) return "WAITING";
            level.removeBlock(CHUNK_LINK, false); level.removeBlock(CHUNK_LINK.below(), false); level.removeBlock(CHUNK_TARGET, false);
            level.setChunkForced(CHUNK_TARGET.getX() >> 4, 0, false); level.setChunkForced(CHUNK_LINK.getX() >> 4, 0, false);
            return "PASS chunk_reconnected";
        }
        if (action.equals("normal") || action.equals("advanced")) {
            checks.clear();
            level.setChunkForced(0, 0, true);
            level.removeBlock(COMPUTER, false);
            for (int x = 8; x <= 15; x++) for (int y = 200; y <= 202; y++) for (int z = 8; z <= 15; z++)
                level.removeBlock(new BlockPos(x, y, z), false);
            level.setBlockAndUpdate(COMPUTER, block("computercraft:computer_" + action).defaultBlockState());
            var target = (ComputerBlockEntity) level.getBlockEntity(COMPUTER);
            var player = FakePlayerFactory.getMinecraft(level);
            player.setPos(COMPUTER.getX() + .5, COMPUTER.getY(), COMPUTER.getZ() + 2);
            var item = new ItemStack(PeripheralLinks.ITEM.get(), 3); player.setItemInHand(InteractionHand.MAIN_HAND, item);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(COMPUTER.getCenter(), Direction.UP, COMPUTER, false));
            require(player.gameMode.useItemOn(player, level, item, InteractionHand.MAIN_HAND,
                    new BlockHitResult(COMPUTER.getCenter(), Direction.UP, COMPUTER, false)).consumesAction(), "pairing succeeds through player interaction");
            UUID id = ((LinkIdentity) target).teutonlib$linkTarget();
            require(id != null, "target identity persisted on block");
            var clearItem = item.copy(); player.setItemInHand(InteractionHand.MAIN_HAND, clearItem);
            player.setShiftKeyDown(true); clearItem.use(level, player, InteractionHand.MAIN_HAND); player.setShiftKeyDown(false);
            require(!clearItem.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag().hasUUID("TeutonLibSelectedTarget"), "sneaking clears item selection");
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
            var selectedCopy = item.copy();
            // Exercise actual item placement against a generic inventory, then the runtime registry.
            level.setBlockAndUpdate(FIRST.below(), Blocks.CHEST.defaultBlockState());
            player.setPos(FIRST.getX() + .5, FIRST.getY(), FIRST.getZ() + 2);
            require(player.gameMode.useItemOn(player, level, item, InteractionHand.MAIN_HAND,
                    new BlockHitResult(FIRST.below().getCenter(), Direction.UP, FIRST.below(), false)).consumesAction(), "item placement through chest interaction succeeds");
            var first = (PeripheralLinkBlockEntity) level.getBlockEntity(FIRST);
            require(first != null && id.equals(first.target()), "placement retains selected target");
            require(!item.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag().hasUUID("TeutonLibSelectedTarget"), "successful placement clears remaining stack selection");
            // Failure preserves the selected address; creative placement clears it without consuming the stack.
            player.setItemInHand(InteractionHand.MAIN_HAND, selectedCopy);
            require(!player.gameMode.useItemOn(player, level, selectedCopy, InteractionHand.MAIN_HAND,
                    new BlockHitResult(FIRST.below().getCenter(), Direction.UP, FIRST.below(), false)).consumesAction(), "occupied placement fails");
            require(selectedCopy.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag().hasUUID("TeutonLibSelectedTarget"), "failed placement retains selection");
            var singleItem = selectedCopy.copyWithCount(1);
            var creative = FIRST.south(2); level.setBlockAndUpdate(creative.below(), Blocks.CHEST.defaultBlockState());
            player.setPos(creative.getX()+.5, creative.getY(), creative.getZ()+2);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            int countBefore = selectedCopy.getCount();
            require(player.gameMode.useItemOn(player, level, selectedCopy, InteractionHand.MAIN_HAND,
                    new BlockHitResult(creative.below().getCenter(), Direction.UP, creative.below(), false)).consumesAction(), "creative placement succeeds");
            require(((PeripheralLinkBlockEntity)level.getBlockEntity(creative)).target().equals(id), "creative placed binding retained");
            require(selectedCopy.getCount()==countBefore && !selectedCopy.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag().hasUUID("TeutonLibSelectedTarget"), "creative selection cleared without consumption");
            level.removeBlock(creative,false); level.removeBlock(creative.below(),false);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            level.setBlockAndUpdate(creative.below(), Blocks.CHEST.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, singleItem);
            require(player.gameMode.useItemOn(player, level, singleItem, InteractionHand.MAIN_HAND,
                    new BlockHitResult(creative.below().getCenter(), Direction.UP, creative.below(), false)).consumesAction(), "single-item placement succeeds");
            require(singleItem.isEmpty() && ((PeripheralLinkBlockEntity)level.getBlockEntity(creative)).target().equals(id), "last item consumed after copying binding");
            level.removeBlock(creative,false); level.removeBlock(creative.below(),false);
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
            LinkRegistry.register(first);
            link(level, SECOND, id);
            LinkRegistry.validate();
            require(LinkRegistry.connections(target).size() == 2, "multiple links share one target");
            int limit = de.TeutonStudio.TeutonLib.ServerConfig.PERIPHERAL_LINK_LIMIT.get();
            try {
                de.TeutonStudio.TeutonLib.ServerConfig.PERIPHERAL_LINK_LIMIT.set(1); LinkRegistry.validate();
                require(LinkRegistry.connections(target).size() == 1, "configured network limit enforced");
            } finally { de.TeutonStudio.TeutonLib.ServerConfig.PERIPHERAL_LINK_LIMIT.set(limit); LinkRegistry.validate(); }
            require(first.connection.peripheral().getType().equals("inventory")
                    || first.connection.peripheral().getAdditionalTypes().contains("inventory"), "generic inventory resolved through CC");
            firstName = first.connection.name();
            target.getPersistentData().putString("ExpectedLinkName", firstName);
            target.setChanged();
            savedLink = first.saveWithFullMetadata(level.registryAccess());
            UUID original = first.identity();
            level.removeBlock(FIRST, false); level.setBlockAndUpdate(FIRST, PeripheralLinks.BLOCK.get().defaultBlockState());
            first = (PeripheralLinkBlockEntity) level.getBlockEntity(FIRST);
            first.loadWithComponents(savedLink, level.registryAccess()); LinkRegistry.register(first); LinkRegistry.validate();
            require(first.identity().equals(original) && first.connection.name().equals(firstName), "save/load preserves peripheral name");
            // Boundary fixture: exact 256 world blocks, and one block beyond it.
            BlockPos boundary = COMPUTER.offset(256, 0, 0);
            level.setChunkForced(boundary.getX() >> 4, boundary.getZ() >> 4, true);
            var edge = link(level, boundary, id); LinkRegistry.validate();
            require(edge.connection != null, "inclusive range boundary");
            level.removeBlock(boundary, false);
            var outside = link(level, boundary.east(), id); LinkRegistry.validate();
            require(outside.connection == null && outside.status.equals("range"), "beyond range rejected");
            level.removeBlock(boundary.east(), false);
            level.setChunkForced(boundary.getX() >> 4, boundary.getZ() >> 4, false);
            // A real block in another dimension cannot bind to the overworld anchor.
            ServerLevel nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            nether.setChunkForced(0, 0, true);
            var wrong = link(nether, FIRST, id); LinkRegistry.validate();
            require(wrong.connection == null && wrong.status.equals("dimension"), "different dimension rejected");
            nether.removeBlock(FIRST, false); nether.setChunkForced(0, 0, false);
            LinkRegistry.remove(first); LinkRegistry.validate();
            require(first.connection == null, "unload disconnects");
            LinkRegistry.register(first); LinkRegistry.validate();
            require(first.connection != null, "reload reconnects");
            level.setBlockAndUpdate(FIRST.below(), Blocks.STONE.defaultBlockState()); LinkRegistry.validate();
            require(first.connection == null && first.status.equals("no_peripheral"), "missing peripheral disconnects");
            level.setBlockAndUpdate(FIRST.below(), Blocks.CHEST.defaultBlockState()); LinkRegistry.validate();
            require(first.connection != null, "peripheral replacement reconnects");
            ((ChestBlockEntity) level.getBlockEntity(FIRST.below())).setItem(0, new ItemStack(Items.DIAMOND, 16));
            level.setBlockAndUpdate(COMPUTER.east(), Blocks.BARREL.defaultBlockState());
            level.setBlockAndUpdate(COMPUTER.south(), block("computercraft:wired_modem_full").defaultBlockState());
            level.setBlockAndUpdate(COMPUTER.south().east(), Blocks.BARREL.defaultBlockState());
            ((dan200.computercraft.shared.peripheral.modem.wired.WiredModemFullBlockEntity)
                    level.getBlockEntity(COMPUTER.south())).use(player);
            // A disk drive connected through a third link mounts in the actual computer filesystem.
            BlockPos drive = new BlockPos(10, 200, 12);
            level.setBlockAndUpdate(drive, block("computercraft:disk_drive").defaultBlockState());
            var diskDrive = (DiskDriveBlockEntity) level.getBlockEntity(drive);
            diskDrive.setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:disk"))));
            var driveLink = link(level, drive.above(), id);
            level.setBlockAndUpdate(drive, block("computercraft:disk_drive").defaultBlockState());
            ((DiskDriveBlockEntity) level.getBlockEntity(drive)).setItem(0,
                    new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:disk"))));
            LinkRegistry.validate();
            String secondName = ((PeripheralLinkBlockEntity) level.getBlockEntity(SECOND)).connection.name();
            String driveName = driveLink.connection.name();
            BlockPos monitorPos = new BlockPos(10, 200, 14);
            var monitorLink = link(level, monitorPos.above(), id);
            level.setBlockAndUpdate(monitorPos, block("computercraft:monitor_advanced").defaultBlockState());
            BlockPos tankA = new BlockPos(12, 200, 14), tankB = new BlockPos(14, 200, 14);
            var tankLinkA = link(level, tankA.above(), id); var tankLinkB = link(level, tankB.above(), id);
            level.setBlockAndUpdate(tankA, block("create:fluid_tank").defaultBlockState());
            level.setBlockAndUpdate(tankB, block("create:fluid_tank").defaultBlockState());
            var fluid = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, tankA, Direction.UP);
            require(fluid != null && fluid.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 4000, "fluid fixture filled");
            LinkRegistry.validate();
            computer = target.createServerComputer();
            String program = """
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
                    """.formatted(firstName, secondName, driveName, monitorLink.connection.name(), monitorLink.connection.name(),
                    tankLinkA.connection.name(), tankLinkB.connection.name(), tankLinkB.connection.name(), tankLinkA.connection.name());
            var mount = computer.createRootMount();
            if (mount.exists("link-result")) mount.delete("link-result");
            try (var file = mount.openFile("startup.lua", Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                file.write(StandardCharsets.UTF_8.encode(program));
            }
            computer.turnOn();
            return "STARTED " + action + " checks=" + checks.size();
        }
        if (action.equals("result")) {
            var mount = computer.createRootMount();
            if (!mount.exists("link-result")) return "WAITING";
            try (var file = mount.openForRead("link-result")) {
                var buffer = ByteBuffer.allocate(4096); file.read(buffer); buffer.flip();
                return StandardCharsets.UTF_8.decode(buffer).toString();
            }
        }
        if (action.equals("detach")) {
            level.removeBlock(FIRST, false); LinkRegistry.validate();
            computer.queueEvent("test_detached"); return "DETACH_REQUESTED";
        }
        if (action.equals("reconnect")) {
            level.setBlockAndUpdate(FIRST, PeripheralLinks.BLOCK.get().defaultBlockState());
            var first = (PeripheralLinkBlockEntity) level.getBlockEntity(FIRST);
            first.loadWithComponents(savedLink, level.registryAccess()); LinkRegistry.register(first); LinkRegistry.validate();
            computer.queueEvent("test_reconnected"); return "RECONNECT_REQUESTED";
        }
        if (action.equals("conflict")) {
            var target = level.getBlockEntity(COMPUTER);
            BlockPos other = COMPUTER.south(4);
            level.setBlockAndUpdate(other, target.getBlockState());
            var copy = level.getBlockEntity(other);
            copy.loadWithComponents(target.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            LinkRegistry.registerAnchor(copy); LinkRegistry.validate();
            require(LinkRegistry.connections(target).isEmpty(), "copied UUID disables target");
            level.removeBlock(other, false); LinkRegistry.validate();
            require(LinkRegistry.connections(target).isEmpty(), "conflict stays disabled after removal");
            return "PASS conflict";
        }
        if (action.equals("persistence")) {
            level.getChunkAt(COMPUTER);
            var target = level.getBlockEntity(COMPUTER);
            var first = (PeripheralLinkBlockEntity) level.getBlockEntity(FIRST);
            LinkRegistry.registerAnchor(target); LinkRegistry.register(first); LinkRegistry.validate();
            require(("cc_aeroworks:link_" + first.identity()).equals(target.getPersistentData().getString("ExpectedLinkName")), "server restart preserves link name");
            require(LinkRegistry.connections(target).isEmpty(), "UUID conflict remains disabled after server restart");
            return "PASS persistence";
        }
        throw new IllegalArgumentException("Unknown action " + action);
    }
}
