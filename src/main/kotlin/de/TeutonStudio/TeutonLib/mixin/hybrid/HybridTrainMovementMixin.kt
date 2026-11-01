package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.TravellingPoint
import com.simibubi.create.content.trains.graph.TrackGraph
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Carriage::class)
abstract class HybridTrainMovementMixin {
    @Inject(method = ["travel(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/trains/graph/TrackGraph;DLcom/simibubi/create/content/trains/entity/TravellingPoint;Lcom/simibubi/create/content/trains/entity/TravellingPoint;I)D"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$parkWhileEditing`(level: Level?, graph: TrackGraph?, distance: Double,
        first: TravellingPoint?, last: TravellingPoint?, type: Int, cir: CallbackInfoReturnable<Double>) {
        val train = (this as Any as Carriage).train ?: return
        if (HybridTrainRuntime.editing(train)) {
            train.speed = 0.0
            train.targetSpeed = 0.0
            cir.returnValue = 0.0
        }
    }
}
