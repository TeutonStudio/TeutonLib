package de.TeutonStudio.TeutonLib.mixin.hose.client

import com.simibubi.create.content.contraptions.pulley.AbstractPulleyVisual
import com.simibubi.create.content.contraptions.pulley.HosePulleyVisual
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.base.ShaftVisual
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadPolicy
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.visual.util.SmartRecycler
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** The Flywheel path needs the same selective replacement as the fallback renderer. */
@Mixin(AbstractPulleyVisual::class, remap = false)
abstract class AbstractPulleyVisualMixin(context: VisualizationContext, owner: KineticBlockEntity, partialTick: Float) :
    ShaftVisual<KineticBlockEntity>(context, owner, partialTick) {
    @Shadow @Final private lateinit var magnet: TransformedInstance
    @Shadow @Final private lateinit var rope: SmartRecycler<Boolean, TransformedInstance>

    @Inject(
        method = ["animate"],
        at = [At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/processing/burner/ScrollInstance;setChanged()V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )],
        cancellable = true
    )
    private fun `teutonlib$replaceNativeHose`(ci: CallbackInfo) {
        if (this as Any !is HosePulleyVisual) return
        // Let Create repopulate its rope recycler when the physical head is disabled.
        if (!HoseHeadPolicy.enabled(blockEntity)) return
        magnet.setVisible(false)
        magnet.setChanged()
        rope.resetCount()
        rope.discardExtra()
        ci.cancel()
    }

}
