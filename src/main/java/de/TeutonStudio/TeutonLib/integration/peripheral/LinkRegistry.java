package de.TeutonStudio.TeutonLib.integration.peripheral;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import de.TeutonStudio.TeutonLib.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Server-thread registry. It never requests a chunk or scans a neighbourhood. */
public final class LinkRegistry {
    private static final Map<UUID, Set<BlockEntity>> ANCHORS = new HashMap<>();
    private static final Set<PeripheralLinkBlockEntity> LINKS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<LinkLuaApi> APIS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<dan200.computercraft.api.peripheral.WorkMonitor, ConsoleRuntimeAccess> CONSOLES = new IdentityHashMap<>();

    public record Connection(PeripheralLinkBlockEntity link, BlockEntity anchor, BlockEntity owner, IPeripheral peripheral) {
        public String name() { return "cc_aeroworks:link_" + link.identity(); }
    }

    public static boolean isTarget(BlockEntity block) {
        return block instanceof ComputerBlockEntity || ModList.get().isLoaded("cc_aeroworks") && ConsoleLinkSupport.isTarget(block);
    }

    public static synchronized void registerAnchor(BlockEntity block) {
        if (!(block.getLevel() instanceof ServerLevel level) || !isTarget(block) || block.isRemoved()) return;
        UUID id = ((LinkIdentity) block).teutonlib$linkTarget();
        if (id == null) return;
        // The block may have received a new identity during explicit re-pairing.
        ANCHORS.forEach((key, value) -> { if (!key.equals(id)) value.remove(block); });
        Set<BlockEntity> entries = ANCHORS.computeIfAbsent(id, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
        entries.removeIf(candidate -> !loaded(candidate));
        entries.add(block);
        if (entries.size() > 1) LINKS.stream().filter(link -> id.equals(link.target())).forEach(link -> link.connection = null);
    }

    public static synchronized void removeAnchor(BlockEntity block) {
        LINKS.forEach(link -> {
            Connection connection = link.connection;
            if (connection != null && (connection.anchor() == block || connection.owner() == block)) link.connection = null;
        });
        ANCHORS.values().forEach(entries -> entries.remove(block));
        ANCHORS.values().removeIf(Set::isEmpty);
    }

    public static synchronized void register(PeripheralLinkBlockEntity link) { LINKS.add(link); }
    public static synchronized void remove(PeripheralLinkBlockEntity link) { LINKS.remove(link); link.connection = null; }
    static synchronized void register(LinkLuaApi api) { APIS.add(api); }
    static synchronized void remove(LinkLuaApi api) { APIS.remove(api); }
    public static synchronized void console(dan200.computercraft.api.lua.IComputerSystem system, ConsoleRuntimeAccess runtime) {
        CONSOLES.put(system.getMainThreadMonitor(), runtime);
    }
    static synchronized ConsoleRuntimeAccess console(dan200.computercraft.api.lua.IComputerSystem system) { return CONSOLES.get(system.getMainThreadMonitor()); }
    public static synchronized void removeConsole(dan200.computercraft.api.lua.IComputerSystem system) { CONSOLES.remove(system.getMainThreadMonitor()); }

    public static boolean loaded(BlockEntity block) {
        return block != null && !block.isRemoved() && block.getLevel() instanceof ServerLevel level
                && blockAt(level, block.getBlockPos()) == block;
    }

    /** Unlike Level.getBlockEntity(), this cannot create/refresh UNKNOWN chunk tickets. */
    public static BlockEntity blockAt(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntities().get(pos);
    }

    public static Vec3 worldPosition(BlockEntity block) {
        return worldPosition((ServerLevel) block.getLevel(), block.getBlockPos());
    }

    public static Vec3 worldPosition(ServerLevel level, BlockPos pos) {
        return ModList.get().isLoaded("sable") ? SableLinkPositions.world(level, pos) : pos.getCenter();
    }

    public static synchronized BlockEntity anchor(ServerLevel level, UUID id) {
        if (id == null || LinkConflicts.get(level.getServer()).contains(id)) return null;
        Set<BlockEntity> entries = ANCHORS.get(id);
        if (entries == null) return null;
        entries.removeIf(block -> !loaded(block));
        if (entries.size() > 1) { LinkConflicts.get(level.getServer()).add(id); return null; }
        return entries.size() == 1 ? entries.iterator().next() : null;
    }

    public static BlockEntity owner(BlockEntity anchor) {
        if (anchor instanceof ComputerBlockEntity) return anchor;
        if (ModList.get().isLoaded("cc_aeroworks")) return ConsoleLinkSupport.owner(anchor);
        return null;
    }

    /** Called on the server thread once per tick, and by guarded main-thread calls. */
    public static synchronized void validate() {
        List<PeripheralLinkBlockEntity> ordered = LINKS.stream().sorted(Comparator.comparing(link -> link.identity().toString())).toList();
        Map<BlockEntity, Integer> counts = new IdentityHashMap<>();
        for (PeripheralLinkBlockEntity link : ordered) link.connection = resolve(link, counts);
    }

    /** Publish a complete snapshot once; computer threads must never observe a
     * temporary disconnect while a healthy connection is being checked. */
    private static Connection resolve(PeripheralLinkBlockEntity link, Map<BlockEntity, Integer> counts) {
        if (!loaded(link) || !(link.getLevel() instanceof ServerLevel level)) { link.status = "unloaded"; return null; }
        BlockEntity anchor = anchor(level, link.target());
        if (anchor == null) { link.status = "target_unavailable"; return null; }
        if (anchor.getLevel() != level) { link.status = "dimension"; return null; }
        double range = ServerConfig.PERIPHERAL_LINK_RANGE.get();
        double distance = worldPosition(link).distanceToSqr(worldPosition(anchor));
        if (!Double.isFinite(distance) || distance > range * range) { link.status = "range"; return null; }
        BlockEntity owner = owner(anchor);
        if (owner == null || !loaded(owner)) { link.status = "no_computer"; return null; }
        int count = counts.getOrDefault(owner, 0);
        if (count >= ServerConfig.PERIPHERAL_LINK_LIMIT.get()) { link.status = "limit"; return null; }
        BlockPos device = link.devicePosition();
        if (!level.hasChunkAt(device)) { link.status = "unloaded"; return null; }
        IPeripheral peripheral = link.peripheral();
        if (peripheral == null) { link.status = "no_peripheral"; return null; }
        counts.put(owner, count + 1);
        link.status = "connected";
        return new Connection(link, anchor, owner, peripheral);
    }

    public static synchronized List<Connection> connections(BlockEntity owner) {
        return LINKS.stream().map(link -> link.connection).filter(Objects::nonNull)
                .filter(connection -> connection.owner() == owner).sorted(Comparator.comparing(Connection::name)).toList();
    }

    public static synchronized Connection connection(String name) {
        return LINKS.stream().map(link -> link.connection).filter(Objects::nonNull)
                .filter(connection -> connection.name().equals(name)).findFirst().orElse(null);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        validate();
        List<LinkLuaApi> apis;
        List<ConsoleRuntimeAccess> consoles;
        synchronized (LinkRegistry.class) { apis = List.copyOf(APIS); consoles = List.copyOf(CONSOLES.values()); }
        consoles.forEach(ConsoleRuntimeAccess::teutonlib$refresh);
        apis.forEach(LinkLuaApi::refresh);
    }

    /** Like Create's Display Link: using the item must precede chest/computer GUIs. */
    @SubscribeEvent public static void interact(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getItemStack().getItem() instanceof PeripheralLinkItem)) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(PeripheralLinks.BLOCK.get()))
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
    }

    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
            chunk.getBlockEntities().values().forEach(LinkRegistry::registerAnchor);
    }

    @SubscribeEvent public static synchronized void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
            for (BlockEntity block : chunk.getBlockEntities().values()) {
                removeAnchor(block);
                if (block instanceof PeripheralLinkBlockEntity link) remove(link);
            }
        }
    }

    @SubscribeEvent public static synchronized void stopped(ServerStoppedEvent event) {
        LINKS.clear(); ANCHORS.clear(); APIS.clear(); CONSOLES.clear();
    }
    @SubscribeEvent public static synchronized void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        for (Set<BlockEntity> entries : List.copyOf(ANCHORS.values()))
            for (BlockEntity block : List.copyOf(entries)) if (block.getLevel() == event.getLevel()) removeAnchor(block);
        for (PeripheralLinkBlockEntity link : List.copyOf(LINKS)) if (link.getLevel() == event.getLevel()) remove(link);
    }
    private LinkRegistry() {}
}
