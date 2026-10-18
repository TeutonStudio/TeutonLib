package de.TeutonStudio.TeutonLib.integration;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CsoMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".mixin.computer.") || mixinClassName.endsWith(".LinkSystemAccessMixin")) {
            var mods = net.neoforged.fml.loading.LoadingModList.get();
            return mods != null && mods.getModFileById("computercraft") != null
                && (!mixinClassName.contains(".computer.console.") || mods.getModFileById("cc_aeroworks") != null)
                && (!mixinClassName.endsWith("DbwClientMixin") || mods.getModFileById("drivebywire") != null);
        }
        if (mixinClassName.contains(".mixin.peripheral.")) {
            var mods = net.neoforged.fml.loading.LoadingModList.get();
            return mods != null && mods.getModFileById("computercraft") != null && mods.getModFileById("create") != null
                    && (!mixinClassName.contains(".peripheral.console.") || mods.getModFileById("cc_aeroworks") != null);
        }
        return !mixinClassName.contains(".mixin.cso.") || CsoAvailability.available();
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
