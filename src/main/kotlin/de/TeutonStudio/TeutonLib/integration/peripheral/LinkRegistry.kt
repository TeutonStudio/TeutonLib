package de.TeutonStudio.TeutonLib.integration.peripheral

import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.WorkMonitor
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import de.TeutonStudio.TeutonLib.ServerConfig
import java.util.Collections
import java.util.Comparator
import java.util.HashMap
import java.util.IdentityHashMap
import java.util.Objects
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Function
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.neoforge.common.util.TriState
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/** Server-thread registry. It never requests a chunk or scans a neighbourhood.  */
object LinkRegistry {
    private val ANCHORS: MutableMap<UUID, MutableSet<BlockEntity>> = HashMap<UUID, MutableSet<BlockEntity>>()
    private val LINKS: MutableSet<PeripheralLinkBlockEntity> =
        Collections.newSetFromMap<PeripheralLinkBlockEntity>(IdentityHashMap<PeripheralLinkBlockEntity, Boolean>())
    private val APIS: MutableSet<LinkLuaApi> =
        Collections.newSetFromMap<LinkLuaApi>(IdentityHashMap<LinkLuaApi, Boolean>())
    private val CONSOLES: MutableMap<WorkMonitor, ConsoleRuntimeAccess> =
        IdentityHashMap<WorkMonitor, ConsoleRuntimeAccess>()

    @JvmStatic
    fun isTarget(block: BlockEntity?): Boolean {
        return block is ComputerBlockEntity || ModList.get().isLoaded("cc_aeroworks") && ConsoleLinkSupport.isTarget(
            block
        )
    }

    @Synchronized
    @JvmStatic
    fun registerAnchor(block: BlockEntity) {
        if ((block.level !is ServerLevel) || !isTarget(block) || block.isRemoved()) return
        val id = (block as LinkIdentity).`teutonlib$linkTarget`()
        if (id == null) return
        // The block may have received a new identity during explicit re-pairing.
        ANCHORS.forEach { (key: UUID, value: MutableSet<BlockEntity>) -> if (key != id) value!!.remove(block) }
        val entries =
            ANCHORS.computeIfAbsent(id) { ignored: UUID? -> Collections.newSetFromMap<BlockEntity>(IdentityHashMap<BlockEntity, Boolean>()) }
        entries.removeIf { candidate: BlockEntity? -> !loaded(candidate) }
        entries.add(block)
        if (entries.size > 1) LINKS.stream().filter { link: PeripheralLinkBlockEntity? -> id == link!!.target() }
            .forEach { link: PeripheralLinkBlockEntity? -> link!!.connection = null }
    }

    @Synchronized
    @JvmStatic
    fun removeAnchor(block: BlockEntity?) {
        LINKS.forEach(Consumer { link: PeripheralLinkBlockEntity? ->
            val connection = link!!.connection
            if (connection != null && (connection.anchor === block || connection.owner === block)) link.connection =
                null
        })
        ANCHORS.values.forEach(Consumer { entries: MutableSet<BlockEntity> -> entries!!.remove(block) })
        ANCHORS.values.removeIf { obj: MutableSet<BlockEntity> -> obj!!.isEmpty() }
    }

    @Synchronized
    @JvmStatic
    fun register(link: PeripheralLinkBlockEntity?) {
        LINKS.add(link!!)
    }

    @Synchronized
    @JvmStatic
    fun remove(link: PeripheralLinkBlockEntity) {
        LINKS.remove(link)
        link.connection = null
    }

    @Synchronized
    @JvmStatic
    fun register(api: LinkLuaApi?) {
        APIS.add(api!!)
    }

    @Synchronized
    @JvmStatic
    fun remove(api: LinkLuaApi?) {
        APIS.remove(api)
    }

    @Synchronized
    @JvmStatic
    fun console(system: IComputerSystem, runtime: ConsoleRuntimeAccess) {
        CONSOLES.put(system.mainThreadMonitor, runtime)
    }

    @Synchronized
    @JvmStatic
    fun console(system: IComputerSystem): ConsoleRuntimeAccess? {
        return CONSOLES.get(system.mainThreadMonitor)
    }

    @Synchronized
    @JvmStatic
    fun removeConsole(system: IComputerSystem) {
        CONSOLES.remove(system.mainThreadMonitor)
    }

    @JvmStatic
    fun loaded(block: BlockEntity?): Boolean {
        return block != null && !block.isRemoved() && block.level is ServerLevel
                && blockAt(block.level as ServerLevel, block.blockPos) === block
    }

    /** Unlike Level.blockEntity, this cannot create/refresh UNKNOWN chunk tickets.  */
    @JvmStatic
    fun blockAt(level: ServerLevel, pos: BlockPos): BlockEntity? {
        val chunk = level.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4)
        return if (chunk == null) null else chunk.blockEntities.get(pos)
    }

    @JvmStatic
    fun worldPosition(block: BlockEntity): Vec3 {
        return worldPosition(block.level as ServerLevel, block.blockPos)
    }

    @JvmStatic
    fun worldPosition(level: ServerLevel, pos: BlockPos): Vec3 {
        return if (ModList.get().isLoaded("sable")) SableLinkPositions.world(level, pos) else pos.center
    }

    @Synchronized
    @JvmStatic
    fun anchor(level: ServerLevel, id: UUID?): BlockEntity? {
        if (id == null || LinkConflicts.Companion.get(level.server).contains(id)) return null
        val entries = ANCHORS.get(id)
        if (entries == null) return null
        entries.removeIf { block: BlockEntity? -> !loaded(block) }
        if (entries.size > 1) {
            LinkConflicts.Companion.get(level.server).add(id)
            return null
        }
        return if (entries.size == 1) entries.iterator().next() else null
    }

    @JvmStatic
    fun owner(anchor: BlockEntity?): BlockEntity? {
        if (anchor is ComputerBlockEntity) return anchor
        if (ModList.get().isLoaded("cc_aeroworks")) return ConsoleLinkSupport.owner(anchor)
        return null
    }

    /** Called on the server thread once per tick, and by guarded main-thread calls.  */
    @Synchronized
    @JvmStatic
    fun validate() {
        val ordered = LINKS.stream()
            .sorted(Comparator.comparing<PeripheralLinkBlockEntity, String>(Function { link: PeripheralLinkBlockEntity? ->
                link!!.identity().toString()
            })).toList()
        val counts: MutableMap<BlockEntity, Int> = IdentityHashMap<BlockEntity, Int>()
        for (link in ordered) link.connection = resolve(link, counts)
    }

    /** Publish a complete snapshot once; computer threads must never observe a
     * temporary disconnect while a healthy connection is being checked.  */
    @JvmStatic
    private fun resolve(link: PeripheralLinkBlockEntity, counts: MutableMap<BlockEntity, Int>): Connection? {
        if (!loaded(link) || link.level !is ServerLevel) {
            link.status = "unloaded"
            return null
        }
        val level = link.level as ServerLevel
        val anchor = anchor(level, link.target())
        if (anchor == null) {
            link.status = "target_unavailable"
            return null
        }
        if (anchor.level !== level) {
            link.status = "dimension"
            return null
        }
        val range = ServerConfig.PERIPHERAL_LINK_RANGE.get().toDouble()
        val distance = worldPosition(link)!!.distanceToSqr(worldPosition(anchor))
        if (!java.lang.Double.isFinite(distance) || distance > range * range) {
            link.status = "range"
            return null
        }
        val owner = owner(anchor)
        if (owner == null || !loaded(owner)) {
            link.status = "no_computer"
            return null
        }
        val count: Int = counts.getOrDefault(owner, 0)!!
        if (count >= ServerConfig.PERIPHERAL_LINK_LIMIT.get()) {
            link.status = "limit"
            return null
        }
        val device = link.devicePosition()
        if (!level.hasChunkAt(device)) {
            link.status = "unloaded"
            return null
        }
        val peripheral = link.peripheral()
        if (peripheral == null) {
            link.status = "no_peripheral"
            return null
        }
        counts.put(owner, count + 1)
        link.status = "connected"
        return Connection(link, anchor, owner, peripheral)
    }

    @Synchronized
    @JvmStatic
    fun connections(owner: BlockEntity?): MutableList<Connection> {
        return LINKS.stream().map<Connection> { link: PeripheralLinkBlockEntity? -> link!!.connection }
            .filter { obj: Connection? -> Objects.nonNull(obj) }
            .filter { connection: Connection? -> connection!!.owner === owner }
            .sorted(Comparator.comparing<Connection, String>(Function { obj: Connection? -> obj!!.name() })).toList()
    }

    @Synchronized
    @JvmStatic
    fun connection(name: String?): Connection? {
        return LINKS.stream().map<Connection> { link: PeripheralLinkBlockEntity? -> link!!.connection }
            .filter { obj: Connection? -> Objects.nonNull(obj) }
            .filter { connection: Connection? -> connection!!.name() == name }.findFirst().orElse(null)
    }

    @SubscribeEvent
    @JvmStatic
    fun tick(event: ServerTickEvent.Post?) {
        validate()
        val apis: MutableList<LinkLuaApi>
        val consoles: MutableList<ConsoleRuntimeAccess>
        synchronized(LinkRegistry::class.java) {
            apis = java.util.List.copyOf<LinkLuaApi>(APIS)
            consoles = java.util.List.copyOf<ConsoleRuntimeAccess>(CONSOLES.values)
        }
        consoles.forEach(Consumer { obj: ConsoleRuntimeAccess -> obj!!.`teutonlib$refresh`() })
        apis.forEach(Consumer { obj: LinkLuaApi? -> obj!!.refresh() })
    }

    /** Like Create's Display Link: using the item must precede chest/computer GUIs.  */
    @SubscribeEvent
    @JvmStatic
    fun interact(event: RightClickBlock) {
        if (event.isCanceled() || event.itemStack.item !is PeripheralLinkItem) return
        if (!event.level.getBlockState(event.pos).`is`(PeripheralLinks.BLOCK.get())) event.setUseBlock(
            TriState.FALSE
        )
    }

    @SubscribeEvent
    @JvmStatic
    fun chunkLoaded(event: ChunkEvent.Load) {
        if (event.level.isClientSide()) return
        val chunk = event.chunk as? LevelChunk ?: return
        chunk.blockEntities.values.forEach { registerAnchor(it) }
    }

    @SubscribeEvent
    @Synchronized
    @JvmStatic
    fun chunkUnloaded(event: ChunkEvent.Unload) {
        if (event.level.isClientSide()) return
        val chunk = event.chunk as? LevelChunk ?: return
        run {
            for (block in chunk.blockEntities.values) {
                removeAnchor(block)
                if (block is PeripheralLinkBlockEntity) remove(block)
            }
        }
    }

    @SubscribeEvent
    @Synchronized
    @JvmStatic
    fun stopped(event: ServerStoppedEvent?) {
        LINKS.clear()
        ANCHORS.clear()
        APIS.clear()
        CONSOLES.clear()
    }

    @SubscribeEvent
    @Synchronized
    @JvmStatic
    fun levelUnloaded(event: LevelEvent.Unload) {
        if (event.level.isClientSide()) return
        for (entries in java.util.List.copyOf<MutableSet<BlockEntity>>(ANCHORS.values)) for (block in java.util.List.copyOf<BlockEntity>(
            entries
        )) if (block!!.level === event.level) removeAnchor(block)
        for (link in java.util.List.copyOf<PeripheralLinkBlockEntity>(LINKS)) if (link!!.level === event.level) LinkRegistry.remove(
            link
        )
    }

    @JvmRecord
    data class Connection(
        val link: PeripheralLinkBlockEntity,
        val anchor: BlockEntity,
        val owner: BlockEntity,
        val peripheral: IPeripheral
    ) {
        fun name(): String {
            return "cc_aeroworks:link_" + link!!.identity()
        }
    }
}
