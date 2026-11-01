package de.TeutonStudio.TeutonLib.mixin.train.client

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(SectionRenderDispatcher::class)
interface TrainPortalSectionDispatcherAccess {
    @Accessor("level")
    fun `teutonlib$getCompileLevel`(): ClientLevel
}
