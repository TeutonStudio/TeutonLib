package de.TeutonStudio.TeutonLib.traintest

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

/** Selects a loaded test world; selecting Aether alone does not create a native Aether portal. */
internal object TrainPortalTestDestination {
    val key: ResourceKey<Level>
        get() {
            val configured = System.getProperty("teutonlib.trainPortalDestination", "minecraft:the_nether")
            val location = requireNotNull(ResourceLocation.tryParse(configured)) {
                "Invalid teutonlib.trainPortalDestination: $configured"
            }
            return ResourceKey.create(Registries.DIMENSION, location)
        }

    fun level(source: ServerLevel): ServerLevel = checkNotNull(source.server.getLevel(key)) {
        "Configured train portal test destination is not loaded: ${key.location()}"
    }
}
