package de.TeutonStudio.TeutonLib.integration.hybrid

import de.TeutonStudio.TeutonLib.ServerConfig
import de.TeutonStudio.TeutonLib.network.HybridTrainStatePayload
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor

object HybridTrainSync {
    private var last: HybridTrainStatePayload? = null
    private fun snapshot(server: MinecraftServer) = HybridTrainStatePayload(ServerConfig.hybridTrains(),
        server.allLevels.flatMap { level -> SubLevelContainer.getContainer(level)?.allSubLevels.orEmpty() }
            .filter { HybridTrainRuntime.locked(it) && HybridTrainRuntime.owningRoot(it) === it }.map { it.uniqueId }.toSet())

    @SubscribeEvent fun login(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        PacketDistributor.sendToPlayer(player, snapshot(player.server))
    }

    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if (!de.TeutonStudio.TeutonLib.integration.TrainPortalCompatibility.active()) {
            event.server.allLevels.forEach(de.TeutonStudio.TeutonLib.integration.train.TrainPortalMachines::tick)
        }
        val next = snapshot(event.server)
        if (next != last) { last = next; PacketDistributor.sendToAllPlayers(next) }
    }

    @SubscribeEvent fun stopped(event: ServerStoppedEvent) { last = null }
}
