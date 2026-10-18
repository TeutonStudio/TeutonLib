package de.TeutonStudio.TeutonLib.mixin

import de.TeutonStudio.TeutonLib.ServerConfig
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.server.ServerLifecycleHooks
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [ServerLifecycleHooks::class], remap = false)
abstract class ServerConfigLocationMixin {
    private companion object {
        @Inject(method = ["handleServerAboutToStart"], at = [At("HEAD")])
        @JvmStatic
        private fun `teutonlib$prepareWorldConfig`(server: MinecraftServer, ci: CallbackInfo?) {
            ServerConfig.prepareWorld(server)
        }
    }
}
