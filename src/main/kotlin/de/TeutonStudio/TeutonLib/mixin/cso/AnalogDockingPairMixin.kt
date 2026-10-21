package de.TeutonStudio.TeutonLib.mixin.cso

import de.TeutonStudio.TeutonLib.integration.cso.docking.AnalogDocking
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity
import dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetPair
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.joml.Vector3d
import org.spongepowered.asm.mixin.*
import org.spongepowered.asm.mixin.injection.*
import org.spongepowered.asm.mixin.injection.callback.*

@Pseudo
@Mixin(targets = ["dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair"], remap = false)
abstract class AnalogDockingPairMixin(level: Level, first: BlockPos, second: BlockPos) :
    MagnetPair<DockingConnectorBlockEntity>(level, first, second) {
    @Unique private fun analogAllowed(): Boolean {
        val a = level.getBlockEntity(blockPos1) as? DockingConnectorBlockEntity ?: return false
        val b = level.getBlockEntity(blockPos2) as? DockingConnectorBlockEntity ?: return false
        return AnalogDocking.pairAllowed(a, b)
    }

    @Inject(method = ["dock"], at = [At("HEAD")], cancellable = true)
    private fun analogDock(force: Boolean, ci: CallbackInfo) {
        if (AnalogDocking.configured() && !analogAllowed()) ci.cancel()
    }

    @ModifyVariable(method = ["dock"], at = At("HEAD"), argsOnly = true)
    private fun analogNoForce(force: Boolean): Boolean = if (AnalogDocking.configured()) false else force

    // Physics ticks also see old pairs briefly. Do not let those attract an unselected
    // partner or act using different negotiated lengths.
    @Inject(method = ["canConnect"], at = [At("HEAD")], cancellable = true)
    private fun analogForce(position: Vector3d, first: Vector3d, second: Vector3d, ci: CallbackInfoReturnable<Boolean>) {
        if (AnalogDocking.configured() && !analogAllowed()) ci.returnValue = false
    }
}
