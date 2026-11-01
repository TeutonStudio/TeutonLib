package de.TeutonStudio.TeutonLib.mixin.hose

import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import de.TeutonStudio.TeutonLib.integration.hose.HoseDrainConnection
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ItemDrainBlockEntity::class, remap = false)
abstract class ItemDrainBlockEntityMixin {
    @Inject(method = ["addBehaviours"], at = [At("TAIL")])
    private fun `teutonlib$hoseSocket`(behaviours: MutableList<BlockEntityBehaviour>, ci: CallbackInfo) {
        behaviours.add(HoseDrainConnection(this as Any as ItemDrainBlockEntity))
    }

    @Inject(method = ["addToGoggleTooltip"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$hoseTooltip`(tooltip: MutableList<Component>, sneaking: Boolean,
        cir: CallbackInfoReturnable<Boolean>) {
        val connection = HoseDrainConnection.get(this as Any as ItemDrainBlockEntity) ?: return
        cir.returnValue = connection.addToGoggleTooltip(tooltip) || cir.returnValue
    }
}
