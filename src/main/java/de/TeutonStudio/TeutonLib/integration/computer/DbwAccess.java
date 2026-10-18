package de.TeutonStudio.TeutonLib.integration.computer;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import de.TeutonStudio.TeutonLib.TeutonLib;
import net.neoforged.fml.ModList;

/** Optional DBW 0.3 runtime bridge. No DBW class is linked when the mod is absent. */
public final class DbwAccess {
    private record Access(Method get, Method set, Method read, Method network, Method remove, Method add) {}
    private static boolean resolved;
    private static Access access;
    private static Access access() {
        if (resolved) return access;
        resolved = true;
        if (!ModList.get().isLoaded("drivebywire")) return null;
        try {
            Class<?> c = Class.forName("edn.stratodonut.drivebywire.wire.WireNetworkManager");
            access = new Access(c.getMethod("get", Level.class), c.getMethod("trySetSignalAt", Level.class, BlockPos.class, String.class, int.class),
                c.getMethod("getSignalAt", BlockPos.class, Direction.class), c.getMethod("getNetwork"),
                c.getMethod("removeConnection", Level.class, BlockPos.class, BlockPos.class, Direction.class, String.class),
                c.getMethod("createConnection", Level.class, BlockPos.class, BlockPos.class, Direction.class, String.class));
        } catch (ReflectiveOperationException e) { TeutonLib.LOGGER.error("Unsupported Drive By Wire runtime", e); }
        return access;
    }
    public static boolean available() { return access() != null; }
    public static void set(BlockEntity host, String name, int value) {
        Access a = access(); if (a == null || host.getLevel() == null) return;
        try { a.set.invoke(null, host.getLevel(), host.getBlockPos(), name, value); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("DbW signal failed", e); }
    }
    public static Direction side(BlockEntity host, String side) {
        Direction front = host.getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
            ? host.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        return switch(side) { case "front" -> front; case "back" -> front.getOpposite(); case "left" -> front.getClockWise();
            case "right" -> front.getCounterClockWise(); case "top" -> Direction.UP; case "bottom" -> Direction.DOWN;
            default -> throw new IllegalArgumentException("Invalid computer side"); };
    }
    public static int read(BlockEntity host, String side) {
        Access a = access(); if (a == null) return 0;
        try { return (int)a.read.invoke(a.get.invoke(null, host.getLevel()), host.getBlockPos(), side(host, side)); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("DbW input failed", e); }
    }
    private static List<?> sinks(BlockEntity host, String name) throws ReflectiveOperationException {
        Access a = access(); if (a == null) return List.of();
        var network = (Map<?,?>)a.network.invoke(a.get.invoke(null, host.getLevel()));
        Object channels = network.get(host.getBlockPos().asLong());
        Object sinks = channels instanceof Map<?,?> map ? map.get(name) : null;
        return sinks instanceof Collection<?> collection ? List.copyOf(collection) : List.of();
    }
    public static int connections(BlockEntity host, String name) {
        try { return sinks(host, name).size(); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("DbW connections failed", e); }
    }
    public static void rename(BlockEntity host, String oldName, String newName, int value) {
        if (!available() || oldName.equals(newName)) return;
        try {
            List<?> sinks = sinks(host, oldName);
            var added = new ArrayList<Object>();
            set(host, newName, value);
            try {
                for (Object sink : sinks) {
                    BlockPos pos = BlockPos.of((long)sink.getClass().getMethod("position").invoke(sink));
                    Direction face = Direction.from3DDataValue((int)sink.getClass().getMethod("direction").invoke(sink));
                    Object result = access.add.invoke(null, host.getLevel(), host.getBlockPos(), pos, face, newName);
                    boolean success = (boolean)result.getClass().getMethod("isSuccess").invoke(result);
                    if (!success) throw new IllegalStateException("DbW could not migrate connection: " + result);
                    added.add(sink);
                }
            } catch (ReflectiveOperationException | RuntimeException failure) {
                for (Object sink : added) {
                    BlockPos pos = BlockPos.of((long)sink.getClass().getMethod("position").invoke(sink));
                    Direction face = Direction.from3DDataValue((int)sink.getClass().getMethod("direction").invoke(sink));
                    access.remove.invoke(null, host.getLevel(), host.getBlockPos(), pos, face, newName);
                }
                set(host, newName, 0);
                throw failure;
            }
            for (Object sink : sinks) {
                BlockPos pos = BlockPos.of((long)sink.getClass().getMethod("position").invoke(sink));
                Direction face = Direction.from3DDataValue((int)sink.getClass().getMethod("direction").invoke(sink));
                access.remove.invoke(null, host.getLevel(), host.getBlockPos(), pos, face, oldName);
            }
            set(host, oldName, 0);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("DbW rename failed", e); }
    }
    public static void remove(BlockEntity host, String name) {
        if (!available()) return;
        try {
            set(host, name, 0);
            for (Object sink : sinks(host, name)) {
                BlockPos pos = BlockPos.of((long)sink.getClass().getMethod("position").invoke(sink));
                Direction face = Direction.from3DDataValue((int)sink.getClass().getMethod("direction").invoke(sink));
                access.remove.invoke(null, host.getLevel(), host.getBlockPos(), pos, face, name);
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("DbW removal failed", e); }
    }
    private DbwAccess() {}
}
