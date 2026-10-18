package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.mixin.computer.client.DbwClientMixin
import net.neoforged.fml.loading.LoadingModList
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

class CsoMixinPlugin : IMixinConfigPlugin {
    override fun onLoad(mixinPackage: String?) {}
    override fun getRefMapperConfig(): String? {
        return null
    }

    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean {
        if (mixinClassName.contains(".mixin.flux.")) {
            val mods = LoadingModList.get()
            return mods != null && mods.getModFileById("fluxnetworks") != null
        }
        if (mixinClassName.contains(".mixin.computer.") || mixinClassName.endsWith(".LinkSystemAccessMixin")) {
            val mods = LoadingModList.get()
            return mods != null && mods.getModFileById("computercraft") != null && (!mixinClassName.contains(".computer.console.") || mods.getModFileById(
                "cc_aeroworks"
            ) != null)
                    && (!mixinClassName.endsWith("DbwClientMixin") || mods.getModFileById("drivebywire") != null)
        }
        if (mixinClassName.contains(".mixin.peripheral.")) {
            val mods = LoadingModList.get()
            return mods != null && mods.getModFileById("computercraft") != null && mods.getModFileById("create") != null && (!mixinClassName.contains(
                ".peripheral.console."
            ) || mods.getModFileById("cc_aeroworks") != null)
        }
        return !mixinClassName.contains(".mixin.cso.") || CsoAvailability.available()
    }

    override fun acceptTargets(myTargets: MutableSet<String>, otherTargets: MutableSet<String>) {}
    override fun getMixins(): MutableList<String>? {
        return null
    }

    override fun preApply(target: String, node: ClassNode, mixin: String, info: IMixinInfo?) {}
    override fun postApply(target: String, node: ClassNode, mixin: String, info: IMixinInfo?) {}
}
