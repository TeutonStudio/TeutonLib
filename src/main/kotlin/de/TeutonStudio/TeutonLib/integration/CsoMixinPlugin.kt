package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.mixin.computer.client.DbwClientMixin
import com.bawnorton.mixinsquared.MixinSquaredBootstrap
import com.bawnorton.mixinsquared.canceller.MixinCancellerRegistrar
import net.neoforged.fml.loading.LoadingModList
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

class TeutonLibMixinPlugin : IMixinConfigPlugin {
    override fun onLoad(mixinPackage: String?) {
        MixinSquaredBootstrap.init()
        MixinCancellerRegistrar.register(LogisticsMixinCanceller())
        MixinCancellerRegistrar.register(FluidPropagationMixinCanceller())
        MixinCancellerRegistrar.register(TrainPortalMixinCanceller())
    }
    override fun getRefMapperConfig(): String? {
        return null
    }

    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean {
        if (mixinClassName.contains(".mixin.tank.")) return TankFluidCompatibility.active()
        if (mixinClassName.contains(".mixin.hybrid.radar.")) return HybridTrainCompatibility.radarActive()
        if (mixinClassName.contains(".mixin.hybrid.swivel.")) return HybridTrainCompatibility.swivelActive()
        if (mixinClassName.contains(".mixin.hybrid.")) return HybridTrainCompatibility.active()
        if (mixinClassName.contains(".mixin.newage.")) return NewAgeMotorCompatibility.active()
        if (mixinClassName.contains(".mixin.mekanism.")) return MekanismModelCompatibility.active()
        if (mixinClassName.contains(".mixin.hose.")) return HosePulleyCompatibility.active()
        if (mixinClassName.contains(".mixin.train.")) return TrainPortalCompatibility.active()
        if (mixinClassName.contains(".mixin.fluids.")) return FluidPropagationCompatibility.active()
        if (mixinClassName.contains(".mixin.logistics.")) return LogisticsCompatibility.active()
        if (mixinClassName.contains(".mixin.flux.")) {
            val mods = LoadingModList.get()
            return mods != null && mods.getModFileById("fluxnetworks") != null
        }
        if (mixinClassName.contains(".mixin.computer.") || mixinClassName.endsWith(".LinkSystemAccessMixin")) {
            val mods = LoadingModList.get()
            return mods != null && mods.getModFileById("computercraft") != null &&
                    (!mixinClassName.endsWith("WirelessModemRangeMixin") || mods.getModFileById("sable") != null) &&
                    (!mixinClassName.endsWith("DbwClientMixin") || mods.getModFileById("drivebywire") != null)
        }
        if (mixinClassName.contains(".mixin.simulated.")) {
            val mods = LoadingModList.get()
            return mods != null && mods.getModFileById("create") != null && mods.getModFileById("sable") != null &&
                    mods.getModFileById("simulated") != null
        }
        if (mixinClassName.contains(".mixin.peripheral.")) {
            val mods = LoadingModList.get()
            return mods != null && mods.getModFileById("computercraft") != null && mods.getModFileById("create") != null &&
                    (!mixinClassName.contains(".peripheral.aeronautics.") ||
                            mods.getModFileById("aeronautics") != null && mods.getModFileById("simulated") != null && mods.getModFileById("sable") != null) &&
                    (!mixinClassName.contains(".peripheral.simulated.") || mods.getModFileById("simulated") != null)
        }
        return !mixinClassName.contains(".mixin.cso.") || CsoAvailability.available()
    }

    override fun acceptTargets(myTargets: MutableSet<String>, otherTargets: MutableSet<String>) {}
    override fun getMixins(): MutableList<String>? {
        MixinSquaredBootstrap.reOrderExtensions()
        return null
    }

    override fun preApply(target: String, node: ClassNode, mixin: String, info: IMixinInfo?) {}
    override fun postApply(target: String, node: ClassNode, mixin: String, info: IMixinInfo?) {}
}
