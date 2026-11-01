package de.TeutonStudio.TeutonLib.integration.hose

import de.TeutonStudio.TeutonLib.TeutonLib
import java.util.function.Supplier
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionResult
import net.neoforged.neoforge.registries.DeferredRegister

/** Registered only after the optional hose integration has passed its compatibility checks. */
object HoseEntities {
    private val ENTITIES: DeferredRegister<EntityType<*>> =
        DeferredRegister.create(Registries.ENTITY_TYPE, TeutonLib.MOD_ID)

    @JvmField
    val HOSE_HEAD: Supplier<EntityType<HoseHeadEntity>> = ENTITIES.register("hose_head", Supplier {
        EntityType.Builder.of(::HoseHeadEntity, MobCategory.MISC)
            .fireImmune()
            .sized(0.625f, 0.625f)
            .clientTrackingRange(10)
            .updateInterval(1)
            .build("teutonlib:hose_head")
    })

    @JvmStatic
    fun register(bus: IEventBus) {
        ENTITIES.register(bus)
        NeoForge.EVENT_BUS.addListener(::useWrenchInAir)
    }

    private fun useWrenchInAir(event: PlayerInteractEvent.RightClickItem) {
        if (BuiltInRegistries.ITEM.getKey(event.itemStack.item).toString() != "create:wrench") return
        val head = HoseHeadEntity.carriedBy(event.level, event.entity) ?: return
        if (!event.level.isClientSide && !head.throwCarried(event.entity)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
    }
}
