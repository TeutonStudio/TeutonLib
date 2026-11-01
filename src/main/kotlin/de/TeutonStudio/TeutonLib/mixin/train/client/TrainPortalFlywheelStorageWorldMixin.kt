package de.TeutonStudio.TeutonLib.mixin.train.client

import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalFlywheelLighting
import dev.ryanhcode.sable.ActiveSableCompanion
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelLightStorage
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.SectionPos
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** IP may draw another world's Flywheel manager while Minecraft.level is temporarily switched. */
@Mixin(value = [SableFlywheelLightStorage::class], remap = false)
abstract class TrainPortalFlywheelStorageWorldMixin {
    @Inject(method = ["sections"], at = [At("HEAD")], require = 1)
    private fun `teutonlib$rememberNativeSections`(sections: LongSet, ci: CallbackInfo) {
        TrainPortalFlywheelLighting.rememberSections(this as SableFlywheelLightStorage, sections)
    }

    @Redirect(method = ["updateLightSections", "removeUnusedSections"], at = At(value = "FIELD",
        target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;"), require = 2)
    private fun `teutonlib$collectFromStorageWorld`(minecraft: Minecraft): ClientLevel =
        (this as SableFlywheelLightStorage).level() as ClientLevel

    @Redirect(method = ["indexForSection"], at = At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getContainingClient(Lnet/minecraft/core/SectionPos;)Ldev/ryanhcode/sable/sublevel/ClientSubLevel;"), require = 1)
    private fun `teutonlib$indexInStorageWorld`(helper: ActiveSableCompanion, section: SectionPos): ClientSubLevel? =
        helper.getContaining((this as SableFlywheelLightStorage).level() as ClientLevel, section) as? ClientSubLevel
}
