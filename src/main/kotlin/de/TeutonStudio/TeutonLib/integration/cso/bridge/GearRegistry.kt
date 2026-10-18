package de.TeutonStudio.TeutonLib.integration.cso.bridge

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel
import net.minecraft.world.level.Level
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

object GearRegistry {
    private val gears = WeakHashMap<Level, MutableSet<KineticBlockEntity>>()
    fun register(be: KineticBlockEntity) {
        val level = be.level ?: return
        if (level.isClientSide || !isCog(be)) return
        gears.getOrPut(level) { Collections.newSetFromMap(IdentityHashMap()) }.add(be)
    }
    fun unregister(be: KineticBlockEntity) {
        val level = be.level ?: return
        gears[level]?.let { set -> set.remove(be); if (set.isEmpty()) gears.remove(level) }
        GearBridgeManager.onGearRemoved(be)
    }
    fun clear() { gears.clear() }
    fun removeLevel(level: Level) { gears.remove(level) }
    fun snapshot(level: Level): List<KineticBlockEntity> {
        val set = gears[level] ?: return emptyList()
        set.removeIf { it.isRemoved || it.level !== level || !isCog(it) }
        return set.toList()
    }
    fun isCog(be: KineticBlockEntity) = ICogWheel.isSmallCog(be.blockState) || ICogWheel.isLargeCog(be.blockState)
}
