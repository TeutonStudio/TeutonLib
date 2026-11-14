package de.TeutonStudio.TeutonLib.integration.peripheral

import de.TeutonStudio.TeutonLib.api.sable.SubLevelInteractionGeometry
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.ChunkWatchEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks

/** Server-only observations and per-player subscriptions. No chunk-loading reads. */
object LinkAntennaRuntime {
    private data class ChunkKey(val dimension: String, val chunk: Long)
    private data class Tracking(val id: UUID, val antenna: Boolean, val expires: Long)
    private data class Removal(val level: ServerLevel, val position: BlockPos, val id: UUID, val after: Long)
    private val watched = mutableMapOf<UUID, MutableSet<ChunkKey>>()
    private val manifests = mutableMapOf<UUID, CompoundTag>()
    private val markers = mutableMapOf<UUID, Tracking>()
    private val removals = mutableListOf<Removal>()
    private val lastRequest = mutableMapOf<UUID, Long>()
    private var antennaSettingsChanged = false

    fun allowRequest(player: ServerPlayer): Boolean {
        val tick = player.server.overworld().gameTime
        if (lastRequest[player.uuid] == tick) return false
        lastRequest[player.uuid] = tick
        return true
    }
    fun removed(link: PeripheralLinkBlockEntity) {
        val level = link.level as? ServerLevel ?: return
        removals.add(Removal(level, link.blockPos.immutable(), link.identity(), level.server.overworld().gameTime + 2))
    }
    fun tick() {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        val now = server.overworld().gameTime
        val live = LinkRegistry.links()
        val catalog = LinkLocations.get(server)
        // Wait for block moves to finish registering their replacement instance before removing an identity.
        removals.removeAll { removal ->
            if (now < removal.after) false else {
                val remembered = catalog.get(removal.id)?.link
                val stillAtOldLocation = remembered?.position == removal.position &&
                    remembered.dimension == removal.level.dimension().location().toString()
                // onRemove already confirmed the block change. A subsequent chunk unload must not
                // resurrect the entry; a moved UUID observed at its new address must be retained.
                if (live.none { it.identity() == removal.id } && stillAtOldLocation &&
                    (LinkRegistry.blockAt(removal.level, removal.position) as? PeripheralLinkBlockEntity)?.identity() != removal.id)
                    catalog.remove(removal.id)
                true
            }
        }
        if (now % 20L != 0L && !antennaSettingsChanged) return
        antennaSettingsChanged = false
        live.forEach(catalog::observe)
        catalog.all().map { it.target }.distinct().forEach { target ->
            LinkRegistry.anchor(server.overworld(), target)?.let(catalog::observeAnchor)
        }
        for (player in server.playerList.players) {
            val dimension = player.level().dimension().location().toString()
            val visible = ListTag()
            catalog.all().map { it.target }.distinct().forEach { target ->
                val anchor = LinkRegistry.anchor(player.serverLevel(), target) ?: return@forEach
                if (anchor.level !== player.level()) return@forEach
                val pos = anchor.blockPos
                val key = ChunkKey(dimension, net.minecraft.world.level.ChunkPos(pos).toLong())
                val tracking = SubLevelInteractionGeometry.trackedBy(player, anchor) ?: (watched[player.uuid]?.contains(key) == true)
                if (!tracking) return@forEach
                visible.add(CompoundTag().also {
                    it.putUUID("id", target); it.putLong("position", pos.asLong())
                    it.putInt("sides", (anchor as LinkIdentity).`teutonlib$linkAntennaSides`())
                })
            }
            val manifest = CompoundTag().also { it.putString("kind", "antennas"); it.putString("dimension", dimension); it.put("anchors", visible) }
            if (manifests[player.uuid] != manifest) {
                manifests[player.uuid] = manifest
                LinkAntennaPayloads.send(player, manifest)
            }
            val marker = markers[player.uuid] ?: continue
            if (now >= marker.expires) { markers.remove(player.uuid); continue }
            val location = if (marker.antenna) anchorLocation(player, marker.id) else linkLocation(player, marker.id)
            if (location == null || location.getString("dimension") != dimension) {
                markers.remove(player.uuid)
                LinkAntennaPayloads.send(player, CompoundTag().also { it.putString("kind", "clear_marker") })
            } else {
                location.putString("kind", "marker"); location.putInt("remaining", (marker.expires - now).toInt())
                LinkAntennaPayloads.send(player, location)
            }
        }
    }
    fun location(location: LinkLocations.Location, loaded: Boolean, player: ServerPlayer): CompoundTag = location.encode().also {
        it.putBoolean("lastKnown", !loaded)
        if (location.dimension == player.level().dimension().location().toString()) {
            val feet = de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry.projectToWorld(player.level(), player.position())
            it.putDouble("distance", feet.distanceTo(location.world))
        }
    }
    fun anchorLocation(player: ServerPlayer, target: UUID): CompoundTag? {
        val anchor = LinkRegistry.anchor(player.serverLevel(), target)
        val known = anchor?.let { LinkLocations.Location.capture(it) } ?: LinkLocations.get(player.server).forTarget(target)
            .firstNotNullOfOrNull { it.anchor } ?: return null
        return location(known, anchor != null, player).also { it.putBoolean("antenna", true); it.putUUID("identity", target) }
    }
    fun linkLocation(player: ServerPlayer, id: UUID): CompoundTag? {
        val live = LinkRegistry.links().firstOrNull { it.identity() == id }
        val known = live?.let { LinkLocations.Location.capture(it) } ?: LinkLocations.get(player.server).get(id)?.link ?: return null
        return location(known, live != null, player).also {
            it.putBoolean("antenna", false); it.putUUID("identity", id)
            it.putString("nickname", live?.nickname() ?: LinkLocations.get(player.server).get(id)?.nickname.orEmpty())
        }
    }
    fun snapshot(player: ServerPlayer, menu: LinkAntennaMenu): CompoundTag {
        val catalog = LinkLocations.get(player.server)
        LinkRegistry.links(menu.target).forEach(catalog::observe)
        return CompoundTag().also { out ->
            out.putString("kind", "overview")
            out.putInt("menu", menu.containerId)
            menu.anchor(player)?.let { anchor ->
                out.putInt("sides", (anchor as LinkIdentity).`teutonlib$linkAntennaSides`())
                out.putBoolean("editable", !player.isSpectator && player.mayUseItemAt(menu.position,
                    net.minecraft.core.Direction.UP, net.minecraft.world.item.ItemStack.EMPTY))
            }
            anchorLocation(player, menu.target)?.let { out.put("anchor", it) }
            out.put("links", ListTag().also { list ->
                catalog.forTarget(menu.target).forEach { entry ->
                    val live = LinkRegistry.links(menu.target).firstOrNull { it.identity() == entry.id }
                    list.add(entry.encode().also { row ->
                        row.putString("status", live?.status ?: "unloaded")
                        row.put("location", linkLocation(player, entry.id)!!)
                    })
                }
            })
        }
    }
    fun setSides(player: ServerPlayer, menu: LinkAntennaMenu, mask: Int) {
        LinkAntennaSides.validate(mask)
        val anchor = menu.anchor(player) ?: error("teutonlib.antenna.edit_denied")
        require(!player.isSpectator && player.mayUseItemAt(menu.position, net.minecraft.core.Direction.UP,
            net.minecraft.world.item.ItemStack.EMPTY)) { "teutonlib.antenna.edit_denied" }
        val identity = anchor as LinkIdentity
        val interaction = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player,
            net.minecraft.world.InteractionHand.MAIN_HAND, menu.position,
            net.minecraft.world.phys.BlockHitResult(menu.position.center, identity.`teutonlib$linkAntennaSide`(), menu.position, false))
        require(!interaction.isCanceled && interaction.useBlock != net.neoforged.neoforge.common.util.TriState.FALSE) {
            "teutonlib.antenna.edit_denied"
        }
        identity.`teutonlib$linkAntennaSides`(mask)
        antennaSettingsChanged = true
    }
    fun mark(player: ServerPlayer, id: UUID, antenna: Boolean) {
        val location = if (antenna) anchorLocation(player, id) else linkLocation(player, id)
        if (location == null || !location.contains("distance")) return
        markers[player.uuid] = Tracking(id, antenna, player.server.overworld().gameTime + 300)
        location.putString("kind", "marker"); location.putInt("remaining", 300)
        LinkAntennaPayloads.send(player, location)
        player.closeContainer()
    }
    fun open(player: ServerPlayer, position: BlockPos, target: UUID) {
        if (player.mainHandItem.item is PeripheralLinkItem || player.offhandItem.item is PeripheralLinkItem) return
        val level = player.serverLevel()
        val anchor = LinkRegistry.anchor(level, target) ?: return
        if (anchor.level !== level || anchor.blockPos != position || LinkLocations.get(player.server).forTarget(target).isEmpty() ||
            !SubLevelInteractionGeometry.withinReach(player, level, position) ||
            !SubLevelInteractionGeometry.mayInteract(player, level, position)) return
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(player.blockInteractionRange()))
        val nearest = LinkAntennaSides.directions((anchor as LinkIdentity).`teutonlib$linkAntennaSides`()).mapNotNull { side ->
            LinkAntennaGeometry.hit(level, position, side, start, end)?.let { side to it }
        }.minByOrNull { it.second.distanceToSqr(start) } ?: return
        val hit = nearest.second
        // Reject a forged click through a wall; the computer itself is the mounting surface.
        val obstruction = level.clip(ClipContext(start, hit, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        if (LinkAntennaGeometry.occluded(obstruction, position, start, hit)) return
        val interaction = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(
            player, net.minecraft.world.InteractionHand.MAIN_HAND, position,
            net.minecraft.world.phys.BlockHitResult(hit, nearest.first, position, false)
        )
        if (interaction.isCanceled || interaction.useBlock == net.neoforged.neoforge.common.util.TriState.FALSE) return
        LinkAntennaMenu.open(player, anchor, target)
    }
    @SubscribeEvent fun sent(event: ChunkWatchEvent.Sent) {
        watched.getOrPut(event.player.uuid) { mutableSetOf() }.add(ChunkKey(event.level.dimension().location().toString(), event.pos.toLong()))
    }
    @SubscribeEvent fun unwatch(event: ChunkWatchEvent.UnWatch) {
        watched[event.player.uuid]?.remove(ChunkKey(event.level.dimension().location().toString(), event.pos.toLong()))
    }
    @SubscribeEvent fun logout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val id = event.entity.uuid
        watched.remove(id); manifests.remove(id); markers.remove(id); lastRequest.remove(id)
    }
    @SubscribeEvent fun stopped(event: ServerStoppedEvent) {
        watched.clear(); manifests.clear(); markers.clear(); removals.clear(); lastRequest.clear()
        antennaSettingsChanged = false
    }
}
