package de.TeutonStudio.TeutonLib.integration.hybrid

import com.simibubi.create.api.contraption.ContraptionType
import com.simibubi.create.api.registry.CreateRegistries
import com.simibubi.create.content.trains.entity.CarriageContraption
import de.TeutonStudio.TeutonLib.TeutonLib
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.registries.BuiltInRegistries
import java.util.Queue
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

/** Native carriage APIs, storage and portal cutoffs remain available to Create and IP. */
class HybridCarriageContraption : CarriageContraption {
    private var constructionBlocks: Set<BlockPos>? = null
    constructor() : super()
    constructor(direction: Direction) : super(direction)
    override fun getType(): ContraptionType = HybridTrainTypes.CARRIAGE.get()

    override fun assemble(world: Level, pos: BlockPos): Boolean {
        constructionBlocks = if (de.TeutonStudio.TeutonLib.integration.HybridTrainCompatibility.active())
            StationBuildRuntime.assemblyBlocks(pos) else null
        return try { super.assemble(world, pos) } finally { constructionBlocks = null }
    }

    override fun addToInitialFrontier(world: Level, pos: BlockPos, direction: Direction?, frontier: Queue<BlockPos>): Boolean {
        constructionBlocks?.let { frontier.addAll(it) }
        return super.addToInitialFrontier(world, pos, direction, frontier)
    }

    override fun moveBlock(world: Level, direction: Direction?, frontier: Queue<BlockPos>, visited: MutableSet<BlockPos>): Boolean {
        val allowed = constructionBlocks
        if (allowed != null && frontier.peek() !in allowed) {
            frontier.poll()?.let(visited::add)
            return true
        }
        return super.moveBlock(world, direction, frontier, visited)
    }

    override fun movementAllowed(state: BlockState, world: Level, pos: BlockPos): Boolean {
        // Simulated normally rejects Create contraptions containing a swivel.
        // Station staging has already disassembled its child into this plot;
        // the Sable-backed carriage restores that child after conversion.
        if (constructionBlocks?.contains(pos) == true &&
            de.TeutonStudio.TeutonLib.integration.HybridTrainCompatibility.swivelActive() &&
            BuiltInRegistries.BLOCK.getKey(state.block).toString() == "simulated:swivel_bearing") return true
        return super.movementAllowed(state, world, pos)
    }
}

object HybridTrainTypes {
    private val registry = DeferredRegister.create(CreateRegistries.CONTRAPTION_TYPE, TeutonLib.MOD_ID)
    val CARRIAGE = registry.register("hybrid_carriage", java.util.function.Supplier {
        ContraptionType(java.util.function.Supplier { HybridCarriageContraption() })
    })
    fun register(bus: IEventBus) = registry.register(bus)
}
