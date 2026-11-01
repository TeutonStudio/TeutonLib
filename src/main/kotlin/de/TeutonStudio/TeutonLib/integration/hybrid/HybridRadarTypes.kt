package de.TeutonStudio.TeutonLib.integration.hybrid

import com.happysg.radar.block.radar.bearing.RadarContraption
import com.simibubi.create.api.contraption.ContraptionType
import com.simibubi.create.api.registry.CreateRegistries
import de.TeutonStudio.TeutonLib.TeutonLib
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

object HybridRadarTypes {
    private val registry = DeferredRegister.create(CreateRegistries.CONTRAPTION_TYPE, TeutonLib.MOD_ID)
    val RADAR = registry.register("radar", java.util.function.Supplier {
        ContraptionType(java.util.function.Supplier { RadarContraption() })
    })
    fun register(bus: IEventBus) = registry.register(bus)
}
