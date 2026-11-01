package de.TeutonStudio.TeutonLib.integration.train

import createtrainfunctionality.SubLevelLinks
import createtrainfunctionality.PortalRefusal
import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.TravellingPoint.IPortalListener
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

/** Standalone ship transfers stay blocked; train-owned transactions have a scoped exception. */
object TrainPortalGuard {
    const val refusal = "A train build may only transfer together with its carriage through the coordinated train portal handoff"

    @JvmStatic fun owns(level: ServerLevel, sub: ServerSubLevel): Boolean =
        SubLevelLinks.linkForSubLevel(sub.uniqueId) != null ||
            SubLevelLinks.isOursEvenIfOrphaned(level, sub.uniqueId) ||
            de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime.trainOwned(sub)

    /** IP also uses virtual carriage sides within one real dimension. Do not rely on
     * CTF's whollyIn(home) test to recognise a train whose side records already changed. */
    @JvmStatic fun portalListener(level: Level?, carriage: Carriage, original: IPortalListener): IPortalListener {
        if (level == null) return original
        return IPortalListener { nodes ->
            if (level is ServerLevel && TrainPortalTransfers.savedBuild(level, carriage)) {
                if (TrainPortalTransfers.allow(level, carriage, nodes)) original.test(nodes)
                else { PortalRefusal.refuses(carriage); true }
            } else original.test(nodes)
        }
    }
}
