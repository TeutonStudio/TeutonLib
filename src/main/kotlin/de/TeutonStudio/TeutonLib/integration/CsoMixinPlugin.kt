package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.mixin.computer.client.DbwClientMixin
import de.TeutonStudio.TeutonLib.compat.MobileEnvironment
import net.neoforged.fml.loading.LoadingModList
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

class TeutonLibMixinPlugin : IMixinConfigPlugin {
    override fun onLoad(mixinPackage: String?) {}
    override fun getRefMapperConfig(): String? {
        return null
    }

    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean {
        if (mixinClassName.contains(".mixin.mobile.")) {
            if (!MobileEnvironment.isMobileEnabled()) return false
            return when {
                mixinClassName.endsWith("PonderRenderTargetMixin") ||
                    mixinClassName.endsWith("CatnipStencilElementMixin") -> hasVersion("ponder", "1.0.82+mc1.21.1")
                mixinClassName.endsWith("PowerGridNativeMixin") -> hasVersion("powergrid", "0.5.5.1")
                mixinClassName.endsWith("ImmersiveMelodiesMidiMixin") -> hasVersion("immersive_melodies", "0.6.4+1.21.1")
                mixinClassName.endsWith("VisualRecipeEditorBackupMixin") -> hasVersion("visual_recipe_editor", "1.2.1")
                // Essential uses its own staged loader. @Pseudo and require=0 keep this optional.
                mixinClassName.endsWith("EssentialDiscordIpcMixin") -> hasVersion("essential", "1.3.10.9") ||
                    LoadingModList.get()?.getModFileById("essential") == null
                else -> false
            }
        }
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

    private fun hasVersion(modId: String, expected: String): Boolean {
        val file = LoadingModList.get()?.getModFileById(modId) ?: return false
        return file.mods.any { it.modId == modId && it.version.toString() == expected }
    }

    override fun acceptTargets(myTargets: MutableSet<String>, otherTargets: MutableSet<String>) {}
    override fun getMixins(): MutableList<String>? {
        return null
    }

    override fun preApply(target: String, node: ClassNode, mixin: String, info: IMixinInfo?) {}
    override fun postApply(target: String, node: ClassNode, mixin: String, info: IMixinInfo?) {}
}
