package de.TeutonStudio.TeutonLib.integration.hose

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.sable.HoseConnectorPhysics
import java.util.function.Supplier
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge

/** Only the force group is ours; the hose socket is Create's existing Item Drain. */
object HoseConnectors {
    private val forces = net.neoforged.neoforge.registries.DeferredRegister.create(
        dev.ryanhcode.sable.api.physics.force.ForceGroups.REGISTRY_KEY, TeutonLib.MOD_ID)
    @JvmField val TENSION: Supplier<dev.ryanhcode.sable.api.physics.force.ForceGroup> = forces.register("hose_tension", Supplier {
        dev.ryanhcode.sable.api.physics.force.ForceGroup(net.minecraft.network.chat.Component.translatable("teutonlib.hose_connector.force"),
            net.minecraft.network.chat.Component.translatable("teutonlib.hose_connector.force"), 0xCF8248, true)
    })

    fun register(bus: IEventBus) {
        forces.register(bus)
        NeoForge.EVENT_BUS.register(HoseConnectorPhysics)
    }
}
