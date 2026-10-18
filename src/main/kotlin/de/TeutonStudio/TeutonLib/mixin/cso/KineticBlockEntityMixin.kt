package de.TeutonStudio.TeutonLib.mixin.cso

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import de.TeutonStudio.TeutonLib.integration.cso.bridge.ClientGearRegistry
import de.TeutonStudio.TeutonLib.integration.cso.bridge.GearRegistry
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [KineticBlockEntity::class], priority = 700)
abstract class KineticBlockEntityMixin {
    @Inject(method = ["initialize"], at = [At("TAIL")], remap = false)
    private fun cso_registerCogwheel(ci: CallbackInfo) {
        val self = this as KineticBlockEntity
        if (self.level?.isClientSide == true) ClientGearRegistry.register(self) else GearRegistry.register(self)
    }
    @Inject(method = ["remove"], at = [At("HEAD")], remap = false)
    private fun cso_unregisterCogwheel(ci: CallbackInfo) {
        val self = this as KineticBlockEntity
        if (self.level?.isClientSide == true) ClientGearRegistry.unregister(self) else GearRegistry.unregister(self)
    }
}
