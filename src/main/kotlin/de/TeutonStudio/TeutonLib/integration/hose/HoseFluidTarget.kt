package de.TeutonStudio.TeutonLib.integration.hose

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/** Native Create fluid behaviours consume world positions after Sable's handler projection. */
object HoseFluidTarget {
    @JvmStatic
    fun resolve(behaviour: BlockEntityBehaviour, original: BlockPos): BlockPos? {
        val pulley = behaviour.blockEntity as? HosePulleyBlockEntity ?: return original
        val level = pulley.level as? ServerLevel ?: return original
        val host = pulley as? HosePulleyHost ?: return original
        if (!HoseHeadPolicy.enabled(pulley)) return original
        // A missing/unloaded head must not reactivate Sable's previously cached source position.
        if (pulley.isRemoved || level.getBlockEntity(pulley.blockPos) !== pulley) return null
        val head = host.physicalHeadId?.let(level::getEntity) as? HoseHeadEntity ?: return null
        if (!head.isAlive || !head.belongsTo(pulley.blockPos)) return null
        if (head.dockId != null || !de.TeutonStudio.TeutonLib.integration.sable.HoseCablePhysics.ready(head)) return null
        val target = head.blockPosition()
        return if (level.hasChunkAt(target)) target.immutable() else null
    }
}
