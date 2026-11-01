package de.TeutonStudio.TeutonLib.integration.hybrid

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import com.simibubi.create.content.trains.entity.Train
import de.TeutonStudio.TeutonLib.ServerConfig
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.EventPriority

object HybridTrainCommands {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun breakBlock(event: BlockEvent.BreakEvent) {
        val level = event.player.level()
        if (!HybridTrainRuntime.permits(level, event.pos, event.state, Blocks.AIR.defaultBlockState())) event.isCanceled = true
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun dismantle(event: PlayerInteractEvent.RightClickBlock) {
        if (event.entity.isShiftKeyDown && event.itemStack.item is com.simibubi.create.content.equipment.wrench.WrenchItem &&
            !HybridTrainRuntime.permits(event.level, event.pos, event.level.getBlockState(event.pos), Blocks.AIR.defaultBlockState())) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent fun register(event: RegisterCommandsEvent) {
        val train = Commands.literal("train").requires { it.hasPermission(2) }
        for ((name, mode) in listOf("edit" to HybridTrainRuntime.Mode.EDITING, "run" to HybridTrainRuntime.Mode.DRIVING)) {
            train.then(Commands.literal(name).executes { context ->
                try {
                    check(ServerConfig.hybridTrains()) { "Hybrid trains are disabled in this world's server config" }
                    val count = HybridTrainRuntime.changeMode(context.source.level, nearest(context.source), mode)
                    context.source.sendSuccess({ Component.translatable("teutonlib.train.mode_changed", mode.name, count) }, false)
                    count
                } catch (failure: IllegalStateException) {
                    context.source.sendFailure(Component.literal(failure.message ?: "Cannot change train mode")); 0
                }
            })
        }
        train.then(Commands.literal("status").executes { context ->
            try {
                val selected = nearest(context.source)
                context.source.sendSuccess({ Component.literal("${selected.id}: " +
                    if (HybridTrainRuntime.editing(selected)) "EDITING" else "DRIVING") }, false)
                1
            } catch (failure: IllegalStateException) {
                context.source.sendFailure(Component.literal(failure.message ?: "No nearby train")); 0
            }
        })
        event.dispatcher.register(Commands.literal("teutonlib").then(train))
    }

    private fun nearest(source: CommandSourceStack): Train {
        val entities = source.level.getEntitiesOfClass(CarriageContraptionEntity::class.java,
            net.minecraft.world.phys.AABB.ofSize(source.position, 64.0, 64.0, 64.0)) { it.isAlive && it.carriage?.train != null }
        val candidate = entities.minByOrNull { it.boundingBox.center.distanceToSqr(source.position) }
        return checkNotNull(candidate?.carriage?.train) { "Stand within 32 blocks of a loaded train" }
    }
}
