package de.TeutonStudio.TeutonLib.integration.peripheral;

import dan200.computercraft.api.component.ComputerComponents;
import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import de.TeutonStudio.TeutonLib.TeutonLib;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import java.util.*;

public final class LinkLuaApi implements ILuaAPI {
    private static final Map<WorkMonitor, LinkLuaApi> ACCESS = Collections.synchronizedMap(new IdentityHashMap<>());
    private final IComputerSystem system;
    private volatile BlockEntity owner;
    private final boolean portable;
    private volatile Map<String, LinkDevice> devices = Map.of();
    private volatile Map<String, IPeripheral> peripherals = Map.of();
    private volatile boolean running;

    public LinkLuaApi(IComputerSystem system) {
        this.system = system;
        portable = system.getComponent(ComputerComponents.POCKET) != null || system.getComponent(ComputerComponents.TURTLE) != null;
    }
    @Override public String[] getNames() { return new String[]{"teutonlib_links"}; }
    @Override public String getModuleName() { return "teutonlib.links"; }
    @Override public void startup() {
        running = true;
        ACCESS.put(system.getMainThreadMonitor(), this);
        LinkRegistry.register(this);
    }
    @Override public synchronized void shutdown() {
        running = false;
        LinkRegistry.remove(this);
        ACCESS.remove(system.getMainThreadMonitor(), this);
        for (LinkDevice device : devices.values()) close(device);
        devices = Map.of(); peripherals = Map.of();
    }
    public static Map<String, IPeripheral> available(WorkMonitor monitor) {
        LinkLuaApi api = ACCESS.get(monitor);
        return api == null ? Map.of() : api.available();
    }
    Map<String, IPeripheral> available() {
        if (system.getLevel().getServer().isSameThread()) LinkRegistry.validate();
        var result = new LinkedHashMap<String, IPeripheral>();
        peripherals.forEach((name, peripheral) -> {
            LinkRegistry.Connection current = LinkRegistry.connection(name);
            if (current != null && current.owner() == owner && LinkBinding.equivalent(current.peripheral(), peripheral))
                result.put(name, peripheral);
        });
        return result;
    }
    public synchronized void refresh() {
        if (!running) return;
        // CC intentionally denies spatial access while its API factories run.
        // Resolve the live host only after activation, on the server thread.
        BlockEntity host = LinkRegistry.blockAt(system.getLevel(), system.getPosition());
        owner = !portable && LinkRegistry.isTarget(host) ? host : null;
        if (owner == null) {
            devices.values().forEach(LinkLuaApi::close); publish(Map.of()); return;
        }
        ConsoleRuntimeAccess console = LinkRegistry.console(system);
        if (console != null) {
            Map<String, LinkDevice> next = console.teutonlib$devices();
            publish(next);
            return;
        }
        if (!(owner instanceof ComputerBlockEntity)) { publish(Map.of()); return; }
        Map<String, LinkDevice> next = new LinkedHashMap<>();
        for (LinkRegistry.Connection connection : LinkRegistry.connections(owner)) {
            LinkDevice previous = devices.get(connection.name());
            if (previous instanceof LinkBinding binding && binding.matches(connection)) next.put(connection.name(), binding);
            else next.put(connection.name(), new LinkBinding(this, system, connection));
        }
        for (var entry : devices.entrySet()) if (next.get(entry.getKey()) != entry.getValue()) {
            close(entry.getValue()); system.queueEvent("peripheral_detach", entry.getKey());
        }
        Map<String, LinkDevice> old = devices;
        publish(next);
        for (var entry : next.entrySet()) if (old.get(entry.getKey()) != entry.getValue()) {
            try { ((LinkBinding) entry.getValue()).attach(); system.queueEvent("peripheral", entry.getKey()); }
            catch (RuntimeException failure) {
                TeutonLib.LOGGER.warn("Could not attach Peripheral Link {}", entry.getKey(), failure);
                var working = new LinkedHashMap<>(devices); working.remove(entry.getKey()); publish(working);
            }
        }
    }
    private void publish(Map<String, LinkDevice> next) {
        devices = Collections.unmodifiableMap(new LinkedHashMap<>(next));
        var available = new LinkedHashMap<String, IPeripheral>();
        next.forEach((name, device) -> available.put(name, device.peripheral()));
        peripherals = Collections.unmodifiableMap(available);
    }
    private static void close(LinkDevice device) {
        try { device.close(); } catch (RuntimeException failure) { TeutonLib.LOGGER.warn("Could not detach Peripheral Link", failure); }
    }
    private LinkDevice device(String name) throws LuaException {
        LinkDevice device = devices.get(name);
        if (device == null) throw new LuaException("Peripheral '" + name + "' is no longer attached");
        return device;
    }
    @LuaFunction("getNames") public final List<String> names() { return List.copyOf(devices.keySet()); }
    @LuaFunction public final boolean isPresent(String name) { return devices.containsKey(name); }
    @LuaFunction public final MethodResult getType(String name) {
        LinkDevice device = devices.get(name);
        if (device == null) return MethodResult.of((Object) null);
        var types = new LinkedHashSet<String>(); types.add(device.peripheral().getType()); types.addAll(device.peripheral().getAdditionalTypes());
        return MethodResult.of(types.toArray());
    }
    @LuaFunction public final List<String> getMethods(String name) throws LuaException { return device(name).methods().stream().sorted().toList(); }
    @LuaFunction public final Object wrap(String name) throws LuaException { return device(name).handle(); }
    @LuaFunction public final MethodResult call(ILuaContext context, IArguments arguments) throws LuaException {
        return device(arguments.getString(0)).call(context, arguments.getString(1), arguments.drop(2));
    }
    public static Map<String, Object> describe(LinkRegistry.Connection connection) {
        String dimension = connection.link().getLevel().dimension().location().toString();
        var result = new LinkedHashMap<String, Object>();
        result.put("address", connection.name());
        result.put("connectionType", "wireless");
        result.put("linkPosition", position(connection.link().getBlockPos(), dimension));
        result.put("targetPosition", position(connection.link().devicePosition(), dimension));
        result.put("targetId", connection.link().target().toString());
        return result;
    }
    private static Map<String, Object> position(net.minecraft.core.BlockPos pos, String dimension) {
        return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ(), "dimension", dimension);
    }
}
