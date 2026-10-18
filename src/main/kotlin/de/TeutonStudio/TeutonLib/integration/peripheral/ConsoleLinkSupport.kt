@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package de.TeutonStudio.TeutonLib.integration.peripheral

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.world.level.block.entity.BlockEntity

/** Isolates all optional Aeroworks type checks from the CC-only registry.  */
object ConsoleLinkSupport {
    @JvmStatic
    fun isTarget(block: BlockEntity?): Boolean {
        return block is ConsoleBlockEntity
    }

    @JvmStatic
    fun owner(anchor: BlockEntity?): BlockEntity? {
        if (anchor !is ConsoleBlockEntity) return null
        val snapshot = ConsoleMultiblockManager.resolve(anchor.level!!, anchor.blockPos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE) return null
        val owner = snapshot.owner
        return if (owner == null) null else owner.host
    }
}
