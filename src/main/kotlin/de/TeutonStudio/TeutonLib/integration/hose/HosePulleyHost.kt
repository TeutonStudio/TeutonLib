package de.TeutonStudio.TeutonLib.integration.hose

import net.minecraft.core.BlockPos
import java.util.UUID

/** Persistent identity keeps a long or temporarily unloaded hose from spawning duplicate heads. */
interface HosePulleyHost {
    var physicalHeadEnabled: Boolean
    var physicalHeadId: UUID?
    var physicalHeadPosition: BlockPos?
    var physicalHeadMissingTicks: Int
}
