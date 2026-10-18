package de.TeutonStudio.TeutonLib.integration.peripheral;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.*;
import dan200.computercraft.core.computer.GuardedLuaContext;
import dan200.computercraft.core.methods.PeripheralMethod;
import dan200.computercraft.shared.computer.core.ServerContext;
import java.util.*;

/** One real CC attachment, with its own mount ownership and irreversible handle lifetime. */
final class LinkBinding implements LinkDevice, IComputerAccess, GuardedLuaContext.Guard {
    private final LinkLuaApi api;
    private final IComputerSystem system;
    private final LinkRegistry.Connection connection;
    private final Map<String, PeripheralMethod> methods;
    private final Set<String> mounts = new HashSet<>();
    private volatile boolean attached;
    private volatile boolean invalidated;
    private volatile boolean closing;

    LinkBinding(LinkLuaApi api, IComputerSystem system, LinkRegistry.Connection connection) {
        this.api = api; this.system = system; this.connection = connection;
        methods = ServerContext.get(system.getLevel().getServer()).peripheralMethods().getSelfMethods(connection.peripheral());
    }
    void attach() {
        attached = true;
        try { peripheral().attach(this); }
        catch (Throwable failure) {
            try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    boolean matches(LinkRegistry.Connection other) {
        return !invalidated && other.owner() == connection.owner() && other.anchor() == connection.anchor()
                && equivalent(peripheral(), other.peripheral());
    }
    static boolean equivalent(IPeripheral first, IPeripheral second) {
        return first == second || first.equals(second) && second.equals(first);
    }
    @Override public IPeripheral peripheral() { return connection.peripheral(); }
    @Override public Set<String> methods() { return methods.keySet(); }
    @Override public boolean checkValid() {
        if (closing) return attached;
        if (!attached || invalidated) return closing && attached;
        if (system.getLevel().getServer().isSameThread()) LinkRegistry.validate();
        LinkRegistry.Connection current = connection.link().connection;
        if (current == null || !matches(current)) invalidated = true;
        return !invalidated;
    }
    private void check() { if (!checkValid()) throw new NotAttachedException(); }
    private void checkLua() throws LuaException { if (!checkValid()) throw new LuaException("Peripheral is no longer attached"); }
    @Override public MethodResult call(ILuaContext context, String method, IArguments arguments) throws LuaException {
        checkLua();
        PeripheralMethod implementation = methods.get(method);
        if (implementation == null) throw new LuaException("No such method " + method);
        return guard(implementation.apply(peripheral(), new GuardedLuaContext(context, this), this, arguments).adjustError(1));
    }
    private MethodResult guard(MethodResult result) {
        if (result.getCallback() == null) return result;
        return MethodResult.yield(result.getResult(), arguments -> {
            checkLua();
            return guard(result.getCallback().resume(arguments));
        }).adjustError(result.getErrorAdjust());
    }
    @Override public Object handle() {
        List<String> names = new ArrayList<>(methods.keySet());
        Collections.sort(names);
        if (!names.contains("getPeripheralInfo")) names.add("getPeripheralInfo");
        String[] methodNames = names.toArray(String[]::new);
        return new IDynamicLuaObject() {
            @Override public String[] getMethodNames() { return methodNames; }
            @Override public MethodResult callMethod(ILuaContext context, int index, IArguments args) throws LuaException {
                checkLua();
                if (index < 0 || index >= methodNames.length) throw new LuaException("Unknown method");
                String name = methodNames[index];
                return name.equals("getPeripheralInfo") && !methods.containsKey(name)
                        ? MethodResult.of(info()) : call(context, name, args);
            }
        };
    }
    @Override public Map<String, Object> info() { check(); return LinkLuaApi.describe(connection); }
    @Override public synchronized void close() {
        if (!attached && mounts.isEmpty()) return;
        // detach may legitimately unmount, so leave the access active during this callback.
        RuntimeException failure = null;
        closing = true;
        try { if (attached) peripheral().detach(this); }
        catch (RuntimeException exception) { failure = exception; }
        finally {
            attached = false;
            invalidated = true;
            closing = false;
            for (String path : List.copyOf(mounts)) {
                try { system.unmount(path); }
                catch (RuntimeException exception) { if (failure == null) failure = exception; else failure.addSuppressed(exception); }
            }
            mounts.clear();
        }
        if (failure != null) throw failure;
    }
    @Override public synchronized String mount(String desired, Mount mount, String drive) {
        check(); String actual = system.mount(desired, mount, drive); if (actual != null) mounts.add(actual); return actual;
    }
    @Override public synchronized String mountWritable(String desired, WritableMount mount, String drive) {
        check(); String actual = system.mountWritable(desired, mount, drive); if (actual != null) mounts.add(actual); return actual;
    }
    @Override public synchronized void unmount(String path) {
        check();
        if (mounts.remove(path)) system.unmount(path);
    }
    @Override public int getID() { check(); return system.getID(); }
    @Override public void queueEvent(String event, Object... args) { check(); system.queueEvent(event, args); }
    @Override public String getAttachmentName() { check(); return connection.name(); }
    @Override public Map<String, IPeripheral> getAvailablePeripherals() {
        check(); var available = new HashMap<>(system.getAvailablePeripherals()); available.putAll(api.available()); return available;
    }
    @Override public IPeripheral getAvailablePeripheral(String name) {
        check(); IPeripheral result = api.available().get(name); return result == null ? system.getAvailablePeripheral(name) : result;
    }
    @Override public WorkMonitor getMainThreadMonitor() { check(); return system.getMainThreadMonitor(); }
}
