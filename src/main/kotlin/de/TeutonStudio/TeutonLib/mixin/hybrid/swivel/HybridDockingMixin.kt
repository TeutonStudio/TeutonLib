package de.TeutonStudio.TeutonLib.mixin.hybrid.swivel

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.spongepowered.asm.mixin.Mixin

/** A FULL native connector owns the helper arm immediately in front of its base. */
@Mixin(DockingConnectorBlockEntity::class, remap = false)
abstract class HybridDockingMixin {
    @WrapMethod(method = ["tick()V"])
    private fun `teutonlib$permitNativeDockingArm`(original: Operation<Void>) {
        val dock = this as Any as DockingConnectorBlockEntity
        val level = dock.level as? ServerLevel ?: run { original.call(); return }
        val body = Sable.HELPER.getContaining(level, dock.blockPos) as? ServerSubLevel
        if (body == null || !HybridTrainRuntime.trainOwned(body)) { original.call(); return }
        val front = dock.blockPos.relative(dock.blockState.getValue(BlockStateProperties.FACING))
        HybridTrainRuntime.machineWrite(level, setOf(dock.blockPos.immutable(), front)) { original.call() }
    }
}
