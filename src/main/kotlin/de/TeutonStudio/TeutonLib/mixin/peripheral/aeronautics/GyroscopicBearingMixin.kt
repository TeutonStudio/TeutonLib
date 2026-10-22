package de.TeutonStudio.TeutonLib.mixin.peripheral.aeronautics

import de.TeutonStudio.TeutonLib.integration.peripheral.heli.GyroscopicBearingAccess
import de.TeutonStudio.TeutonLib.integration.peripheral.heli.HeliMath
import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.gyroscopic_propeller_bearing.GyroscopicPropellerBearingBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import org.joml.Vector3d
import org.joml.Vector3dc
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyVariable

@Mixin(value = [GyroscopicPropellerBearingBlockEntity::class], remap = false)
abstract class GyroscopicBearingMixin : GyroscopicBearingAccess {
    @Shadow
    @Final
    private lateinit var tiltVector: Vector3d

    @Unique
    private var `teutonlib$luaTarget`: Vector3d? = null

    @Unique
    private var `teutonlib$luaTargetTick`: Long = Long.MIN_VALUE

    override fun `teutonlib$currentTiltDirection`(): Vector3d = Vector3d(tiltVector)

    override fun `teutonlib$activeLuaTiltTarget`(): Vector3d? {
        val level = (this as Any as BlockEntity).level ?: return null
        val target = `teutonlib$luaTarget` ?: return null
        return if (HeliMath.targetIsActive(level.gameTime, `teutonlib$luaTargetTick`)) Vector3d(target) else null
    }

    override fun `teutonlib$setLuaTiltTarget`(target: Vector3dc) {
        val level = (this as Any as BlockEntity).level
            ?: throw IllegalStateException("Gyroscopic bearing is not in a level")
        `teutonlib$luaTarget` = Vector3d(target)
        `teutonlib$luaTargetTick` = level.gameTime
    }

    override fun `teutonlib$clearLuaTiltTarget`() {
        `teutonlib$luaTarget` = null
        `teutonlib$luaTargetTick` = Long.MIN_VALUE
    }

    @ModifyVariable(method = ["setTilt"], at = At("HEAD"), argsOnly = true, ordinal = 1)
    private fun `teutonlib$replaceAutomaticTarget`(automaticTarget: Vector3d): Vector3d =
        `teutonlib$activeLuaTiltTarget`() ?: automaticTarget

}
