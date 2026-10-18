package de.TeutonStudio.TeutonLib.integration.computer;

import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.component.ComputerComponents;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import de.TeutonStudio.TeutonLib.api.computer.*;
import de.TeutonStudio.TeutonLib.integration.peripheral.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import java.util.*;

/** API-factory instance; all Lua entry points run on the server thread. */
public final class ComputerRuntime implements ILuaAPI, ComputerContext {
    private final IComputerSystem system;
    private volatile boolean running;
    private final boolean portable;
    public ComputerRuntime(IComputerSystem system) {
        this.system = system;
        portable = system.getComponent(ComputerComponents.POCKET) != null || system.getComponent(ComputerComponents.TURTLE) != null;
    }
    IComputerSystem system() { return system; }
    @Override public String[] getNames() { return new String[]{"teutonlib_channels"}; }
    @Override public String getModuleName() { return "teutonlib.channels"; }
    @Override public void startup() { running = true; if (!portable) ComputerIntegration.start(this); }
    @Override public void shutdown() { running = false; ComputerIntegration.stop(this); }
    @Override public int computerId() { return system.getID(); }
    @Override public ServerLevel level() { return system.getLevel(); }
    @Override public BlockPos position() { return system.getPosition(); }
    @Override public Map<String, IPeripheral> peripherals() { return Map.copyOf(system.getAvailablePeripherals()); }
    @Override public void queueEvent(String event, Object... arguments) { system.queueEvent(event, arguments); }
    public BlockEntity host() {
        if (!running || portable) throw new IllegalStateException("Computer is not available");
        var chunk = level().getChunkSource().getChunkNow(position().getX() >> 4, position().getZ() >> 4);
        BlockEntity host = chunk == null ? null : chunk.getBlockEntities().get(position());
        if (!(host instanceof ComputerBlockEntity)) host = ComputerExtensions.resolveHost(system);
        if (host == null || host.isRemoved() || host.getLevel() != level()) throw new IllegalStateException("Computer block is not loaded");
        if (host instanceof ComputerBlockEntity computer && (computer.getServerComputer() == null
            || computer.getServerComputer().getMainThreadMonitor() != system.getMainThreadMonitor()))
            throw new IllegalStateException("Computer instance is no longer available");
        return host;
    }
    public ChannelBank bank() { return ((ChannelHost)host()).teutonlib$channels(); }
    private void changed() {
        BlockEntity host = host(); host.setChanged();
        host.getLevel().sendBlockUpdated(host.getBlockPos(), host.getBlockState(), host.getBlockState(), 3);
        queueEvent("teutonlib_channels_changed");
    }
    private static <T> T lua(java.util.function.Supplier<T> action) throws LuaException {
        try { return action.get(); } catch (IllegalArgumentException | IllegalStateException e) { throw new LuaException(e.getMessage()); }
    }
    @LuaFunction(mainThread = true) public final void create(IArguments args) throws LuaException {
        String name = args.getString(0), direction = args.getString(1), side = args.optString(2, "");
        createChannel(name, direction, side);
    }
    public void createChannel(String name, String direction, String side) throws LuaException {
        lua(() -> { bank().create(name, direction, side); if (direction.equals("output")) DbwAccess.set(host(), name, 0); changed(); return null; });
    }
    @LuaFunction(mainThread = true) public final void remove(String name) throws LuaException {
        lua(() -> { var e = bank().get(name); if (e.direction().equals("output")) DbwAccess.remove(host(), name); bank().remove(name); changed(); return null; });
    }
    @LuaFunction(mainThread = true) public final void rename(String oldName, String newName) throws LuaException {
        lua(() -> {
            ChannelBank.name(newName); var e = bank().get(oldName);
            if (!oldName.equals(newName) && bank().entries().stream().anyMatch(v -> v.name().equals(newName))) throw new IllegalArgumentException("Channel already exists");
            if (e.direction().equals("output")) DbwAccess.rename(host(), oldName, newName, e.value());
            bank().rename(oldName, newName); changed(); return null;
        });
    }
    @LuaFunction(mainThread = true) public final void setPublic(String name, boolean published) throws LuaException {
        lua(() -> { bank().published(name, published); changed(); return null; });
    }
    @LuaFunction(mainThread = true) public final void set(IArguments args) throws LuaException {
        double value = args.getDouble(1);
        if (!Double.isFinite(value) || value != Math.floor(value) || value < 0 || value > 15) throw new LuaException("Signal must be an integer from 0 to 15");
        setValue(args.getString(0), (int)value);
    }
    public void setValue(String name, int value) throws LuaException {
        lua(() -> { bank().set(name, value); DbwAccess.set(host(), name, value); changed(); return null; });
    }
    private boolean modem() { return peripherals().values().stream().anyMatch(p -> p.getType().equals("modem") || p.getAdditionalTypes().contains("modem")); }
    private ComputerRuntime remote(int id) {
        host(); if (!modem()) throw new IllegalStateException("This computer needs a connected modem");
        var matches = ComputerIntegration.active().stream().filter(r -> r.computerId() == id && r.level() == level())
            .filter(r -> { try { r.host(); return true; } catch (IllegalStateException e) { return false; } }).toList();
        if (matches.isEmpty()) throw new IllegalStateException("Target computer is not available in this dimension");
        if (matches.size() != 1) throw new IllegalStateException("Ambiguous computer ID");
        var target = matches.getFirst(); if (!target.modem()) throw new IllegalStateException("Target computer needs a connected modem");
        return target;
    }
    public List<ChannelView> channelViews() {
        BlockEntity host = host(); var views = new TreeMap<String, ChannelView>();
        for (var e : bank().entries()) views.put(e.name(), new ChannelView(e.name(), e.direction(), e.side(), e.published(),
            e.direction().equals("input") ? DbwAccess.read(host, e.side()) : e.value(),
            e.direction().equals("output") ? DbwAccess.connections(host, e.name()) : 0, "teutonlib", true));
        ComputerExtensions.channels(this, v -> views.putIfAbsent(v.name(), new ChannelView(v.name(), v.direction(), v.side(), v.published(), v.value(), v.connections(), v.provider(), false)));
        return List.copyOf(views.values());
    }
    private static Map<String,Object> describe(ChannelView c) {
        return Map.of("name", c.name(), "direction", c.direction(), "side", c.side(), "public", c.published(), "value", c.value(), "connections", c.connections(), "provider", c.provider());
    }
    @LuaFunction(mainThread = true) public final MethodResult list(IArguments args) throws LuaException {
        boolean remote = args.count() > 0 && args.get(0) != null;
        int id = remote ? args.getInt(0) : computerId();
        return lua(() -> {
            var views = (remote ? remote(id) : this).channelViews();
            var visible = views.stream().filter(c -> !remote || c.published()).map(ComputerRuntime::describe).toList();
            return MethodResult.of(visible, remote ? (long)views.stream().filter(c -> !c.published()).count() : 0L);
        });
    }
    @LuaFunction(mainThread = true) public final int read(IArguments args) throws LuaException {
        boolean remote = args.count() > 1;
        int id = remote ? args.getInt(0) : computerId(); String name = args.getString(remote ? 1 : 0);
        return lua(() -> (remote ? remote(id) : this).channelValue(name));
    }
    private int channelValue(String name) {
        var entry = bank().find(name);
        if (entry != null) return entry.direction().equals("input") ? DbwAccess.read(host(), entry.side()) : entry.value();
        var matches = new ArrayList<ChannelView>();
        ComputerExtensions.channels(this, c -> { if (c.name().equals(name)) matches.add(c); });
        if (matches.isEmpty()) throw new IllegalArgumentException("Unknown channel '" + name + "'");
        return matches.getFirst().value();
    }
    public List<SourceView> sourceViews() {
        var result = new TreeMap<String, SourceView>();
        for (var e : peripherals().entrySet()) {
            var p = e.getValue(); var types = new LinkedHashSet<String>(); types.add(p.getType()); types.addAll(p.getAdditionalTypes());
            String connection = Set.of("front", "back", "left", "right", "top", "bottom").contains(e.getKey()) ? "local" : "wired";
            String device = p.getTarget() instanceof BlockEntity target ? position(target) : "";
            String linkPosition = "";
            if (ModList.get().isLoaded("create")) {
                var link = LinkRegistry.connection(e.getKey());
                if (link != null) { connection = "wireless"; device = link.link().devicePosition().toShortString(); linkPosition = position(link.link()); }
            }
            result.put(e.getKey(), new SourceView(e.getKey(), List.copyOf(types), connection, device, linkPosition));
        }
        ComputerExtensions.sources(this, v -> result.putIfAbsent(v.name(), v));
        return List.copyOf(result.values());
    }
    private static String position(BlockEntity target) { return target.getBlockPos().toShortString(); }
}
