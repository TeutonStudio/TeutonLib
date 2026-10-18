package de.TeutonStudio.TeutonLib.peripheraltest;

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import de.TeutonStudio.TeutonLib.integration.peripheral.*;
import de.teutonstudio.ccaeroworks.computer.ConsoleComputers;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;

final class ConsoleVerification {
    private static final BlockPos OWNER = new BlockPos(40, 200, 8), ANCHOR = OWNER.east(2), MIDDLE = OWNER.east();
    private static final BlockPos LINK = new BlockPos(44, 201, 10);
    private static final BlockPos SENSOR = new BlockPos(44, 200, 12), DRIVE = new BlockPos(46, 200, 12);
    private static ServerComputer computer;
    private static BlockState deskState;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static String run(ServerLevel level, String action) throws Exception {
        if (action.equals("normal") || action.equals("advanced")) {
            level.setChunkForced(2, 0, true);
            for (int x = 39; x <= 47; x++) for (int z = 7; z <= 12; z++) for (int y = 200; y <= 202; y++)
                level.removeBlock(new BlockPos(x, y, z), false);
            deskState = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("aeroworks:control_desk")).defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
            for (int x = 0; x < 3; x++) level.setBlockAndUpdate(OWNER.east(x), deskState);
            ConsoleMultiblockManager.INSTANCE.invalidate(level);
            var anchor = (ConsoleBlockEntity) level.getBlockEntity(ANCHOR);
            var owner = (ConsoleBlockEntity) level.getBlockEntity(OWNER);
            UUID id = UUID.randomUUID(); ((LinkIdentity) anchor).teutonlib$linkTarget(id);
            for (BlockPos pos : new BlockPos[]{LINK, LINK.east(2)}) {
                level.setBlockAndUpdate(pos.below(), Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(pos, PeripheralLinks.BLOCK.get().defaultBlockState());
                var link = (PeripheralLinkBlockEntity) level.getBlockEntity(pos); link.target(id); LinkRegistry.register(link);
            }
            for (BlockPos pos : new BlockPos[]{SENSOR, DRIVE}) {
                String device = pos.equals(SENSOR) ? "simulated:gimbal_sensor" : "computercraft:disk_drive";
                level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(device)).defaultBlockState());
                level.setBlockAndUpdate(pos.above(), PeripheralLinks.BLOCK.get().defaultBlockState());
                ((PeripheralLinkBlockEntity) level.getBlockEntity(pos.above())).target(id);
            }
            ((dan200.computercraft.shared.peripheral.diskdrive.DiskDriveBlockEntity) level.getBlockEntity(DRIVE)).setItem(0,
                    new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:disk"))));
            var link = (PeripheralLinkBlockEntity) level.getBlockEntity(LINK);
            LinkRegistry.validate(); check(link.connection == null && id.equals(link.target()), "empty row must retain inactive pairing");
            ItemStack installed = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:computer_" + action)));
            ConsoleComputers.INSTANCE.install(owner, installed); ConsoleMultiblockManager.INSTANCE.invalidate(level); LinkRegistry.validate();
            check(link.connection != null && link.connection.owner() == owner, "computer at a different row member must own the link");
            // Removing and reinstalling the computer retains the selected member's target identity.
            ItemStack removed = ConsoleComputers.INSTANCE.remove(owner); ConsoleMultiblockManager.INSTANCE.invalidate(level); LinkRegistry.validate();
            check(link.connection == null && id.equals(link.target()), "computer removal must make pairing inactive");
            ConsoleComputers.INSTANCE.install(owner, removed); ConsoleMultiblockManager.INSTANCE.invalidate(level); LinkRegistry.validate();
            check(link.connection != null, "computer reinstallation must reconnect");
            computer = ConsoleComputers.get(owner).createServerComputer();
            String name = link.connection.name();
            String program = """
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
                    """.formatted(name);
            var mount = computer.createRootMount();
            if (mount.exists("link-result")) mount.delete("link-result");
            try (var file = mount.openFile("startup.lua", Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                file.write(StandardCharsets.UTF_8.encode(program));
            }
            computer.turnOn(); return "STARTED console " + action;
        }
        if (action.equals("result")) {
            var mount = computer.createRootMount(); if (!mount.exists("link-result")) return "WAITING";
            try (var file = mount.openForRead("link-result")) {
                var bytes = ByteBuffer.allocate(4096); file.read(bytes); bytes.flip(); return StandardCharsets.UTF_8.decode(bytes).toString();
            }
        }
        if (action.equals("attachments")) {
            var field = dan200.computercraft.shared.peripheral.diskdrive.DiskDriveBlockEntity.class.getDeclaredField("computers");
            field.setAccessible(true);
            var attached = (java.util.Map<?, ?>) field.get(level.getBlockEntity(DRIVE));
            check(attached.size() == 1, "standard and desk APIs must share exactly one physical attachment");
            var snapshot = de.teutonstudio.ccaeroworks.computer.source.InformationSourceSnapshotBuilder.INSTANCE.build(ConsoleComputers.get((ConsoleBlockEntity)level.getBlockEntity(OWNER)));
            check(snapshot.getSources().stream().anyMatch(v -> v.getId().startsWith("peripheral:cc_aeroworks:link_") && v.getDetails().contains("gimbal_sensor")), "console source directory includes linked sensor");
            check(snapshot.getSources().stream().filter(v -> v.getId().startsWith("peripheral:cc_aeroworks:link_")).count() >= 4, "console source directory includes all linked device bindings");
            return "PASS one_attachment";
        }
        if (action.equals("reboot")) {
            var mount = computer.createRootMount();
            if (mount.exists("link-result")) mount.delete("link-result");
            computer.reboot(); return "REBOOT_REQUESTED";
        }
        if (action.equals("split")) {
            level.removeBlock(MIDDLE, false); ConsoleMultiblockManager.INSTANCE.invalidate(level); LinkRegistry.validate();
            computer.queueEvent("test_split"); return "SPLIT_REQUESTED";
        }
        if (action.equals("merge")) {
            level.setBlockAndUpdate(MIDDLE, deskState); ConsoleMultiblockManager.INSTANCE.invalidate(level); LinkRegistry.validate();
            computer.queueEvent("test_merged"); return "MERGE_REQUESTED";
        }
        throw new IllegalArgumentException(action);
    }
}
