package de.TeutonStudio.TeutonLib.mixin.peripheral

import de.TeutonStudio.TeutonLib.integration.peripheral.LuaTiltTarget
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(BlockEntity::class)
abstract class BlockEntityRemovalMixin {
    @Inject(method = ["setRemoved"], at = [At("HEAD")])
    private fun `teutonlib$discardLuaTarget`(ci: CallbackInfo) {
        (this as Any as? LuaTiltTarget)?.`teutonlib$clearLuaTiltTarget`()
    }
}
