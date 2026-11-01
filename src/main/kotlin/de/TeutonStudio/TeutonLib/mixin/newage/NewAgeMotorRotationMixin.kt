package de.TeutonStudio.TeutonLib.mixin.newage

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity
import org.objectweb.asm.Opcodes
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Pseudo
@Mixin(targets = ["org.antarcticgardens.cna.content.motor.MotorBlockEntity"], remap = false)
abstract class NewAgeMotorRotationMixin {
    @ModifyExpressionValue(method = ["updateGeneratedRotation()V"], at = [At(value = "FIELD",
        target = "Lorg/antarcticgardens/cna/content/motor/MotorBlockEntity;speed:F", opcode = Opcodes.GETFIELD)],
        require = 2, expect = 2)
    private fun `teutonlib$useKineticSpeed`(cachedSpeed: Float): Float =
        (this as Any as GeneratingKineticBlockEntity).theoreticalSpeed

    @Inject(method = ["tick()V"], at = [At("TAIL")])
    private fun `teutonlib$restoreMovedMotor`(ci: CallbackInfo) {
        val motor = this as Any as GeneratingKineticBlockEntity
        if (motor.level?.isClientSide != false || motor.isRemoved || motor.hasSource()) return
        // Assembly clears Create's speed/network while New Age retains its separate last-speed
        // cache. Reconcile after New Age computes its current FE/redstone-controlled output.
        if (motor.generatedSpeed != motor.theoreticalSpeed) motor.updateGeneratedRotation()
    }
}
