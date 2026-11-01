package de.TeutonStudio.TeutonLib.integration.hybrid

import com.simibubi.create.api.contraption.ContraptionType
import com.simibubi.create.api.registry.CreateRegistries
import com.simibubi.create.content.trains.entity.CarriageContraption
import de.TeutonStudio.TeutonLib.TeutonLib
import net.minecraft.core.Direction
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

/** Native carriage APIs, storage and portal cutoffs remain available to Create and IP. */
class HybridCarriageContraption : CarriageContraption {
    constructor() : super()
    constructor(direction: Direction) : super(direction)
    override fun getType(): ContraptionType = HybridTrainTypes.CARRIAGE.get()
}

object HybridTrainTypes {
    private val registry = DeferredRegister.create(CreateRegistries.CONTRAPTION_TYPE, TeutonLib.MOD_ID)
    val CARRIAGE = registry.register("hybrid_carriage", java.util.function.Supplier {
        ContraptionType(java.util.function.Supplier { HybridCarriageContraption() })
    })
    fun register(bus: IEventBus) = registry.register(bus)
}
