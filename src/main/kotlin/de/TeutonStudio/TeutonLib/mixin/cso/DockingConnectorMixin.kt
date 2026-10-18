package de.TeutonStudio.TeutonLib.mixin.cso

import de.TeutonStudio.TeutonLib.integration.cso.bridge.ClientGearBridgeManager
import de.TeutonStudio.TeutonLib.integration.cso.bridge.GearBridgeManager
import de.TeutonStudio.TeutonLib.integration.CsoRuntime
import de.TeutonStudio.TeutonLib.client.CsoClient
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Pseudo
@Mixin(targets = ["dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity"], priority = 700, remap = false)
abstract class DockingConnectorMixin {
    @Inject(method = ["tick"], at = [At("TAIL")], remap = false)
    private fun cso_refreshCogBridges(ci: CallbackInfo) {
        val self = this as BlockEntity; val level = self.level ?: return
        if (level.isClientSide) { if (!CsoClient.enabled()) return } else if (!CsoRuntime.enabled()) return
        if (Math.floorMod(self.blockPos.asLong(), 10L) != level.gameTime % 10L) return
        if (level.isClientSide) ClientGearBridgeManager.tickDock(self) else GearBridgeManager.tickDock(self)
    }
    @Inject(method = ["unDock"], at = [At("HEAD")], remap = false)
    private fun cso_removeCogBridges(ci: CallbackInfo) {
        val self = this as BlockEntity
        if (self.level?.isClientSide == true) ClientGearBridgeManager.removeForConnector(self) else GearBridgeManager.removeForConnector(self)
    }
}
