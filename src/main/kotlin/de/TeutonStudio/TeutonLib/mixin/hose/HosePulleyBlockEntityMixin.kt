package de.TeutonStudio.TeutonLib.mixin.hose

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import de.TeutonStudio.TeutonLib.integration.hose.HosePulleyBridge
import de.TeutonStudio.TeutonLib.integration.hose.HosePulleyHost
import de.TeutonStudio.TeutonLib.integration.hose.HoseHeadPolicy
import net.createmod.catnip.animation.LerpedFloat
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.util.UUID

@Mixin(HosePulleyBlockEntity::class, remap = false)
abstract class HosePulleyBlockEntityMixin : HosePulleyHost {
    @Shadow private lateinit var offset: LerpedFloat
    @Shadow private var isMoving: Boolean = false
    @Unique override var physicalHeadEnabled: Boolean = true
    @Unique override var physicalHeadId: UUID? = null
    @Unique override var physicalHeadPosition: BlockPos? = null
    @Unique override var physicalHeadMissingTicks: Int = 0

    @Inject(method = ["addBehaviours"], at = [At("TAIL")])
    private fun `teutonlib$hosePipe`(behaviours: MutableList<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour>, ci: CallbackInfo) {
        val pulley = this as Any as HosePulleyBlockEntity
        behaviours.add(object : com.simibubi.create.content.fluids.FluidTransportBehaviour(pulley) {
            override fun canHaveFlowToward(state: net.minecraft.world.level.block.state.BlockState, direction: net.minecraft.core.Direction): Boolean {
                if (de.TeutonStudio.TeutonLib.integration.hose.HosePipeGraph.connector(pulley.level, pulley.blockPos) == null) return false
                return direction == net.minecraft.core.Direction.DOWN ||
                    com.simibubi.create.content.fluids.hosePulley.HosePulleyBlock.hasPipeTowards(pulley.level, pulley.blockPos, state, direction)
            }
            override fun tick() {
                if (de.TeutonStudio.TeutonLib.integration.hose.HosePipeGraph.connector(pulley.level, pulley.blockPos) != null) super.tick()
            }
        })
    }

    @Inject(method = ["getMovementSpeed"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$limitHoseSpeed`(cir: CallbackInfoReturnable<Float>) {
        val speed = cir.returnValue
        if (speed <= 0) return
        val pulley = this as Any as HosePulleyBlockEntity
        if (!HoseHeadPolicy.enabled(pulley)) return
        val limit = HosePulleyBridge.MAX_LENGTH
        cir.returnValue = minOf(speed, (limit - offset.value).coerceAtLeast(0f))
    }

    // A physical head can move sideways; blocks in Create's vertical column do not limit its hose.
    @WrapOperation(method = ["tick", "onSpeedChanged", "lazyTick"], at = [At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;canBeReplaced()Z")], require = 3)
    private fun `teutonlib$ignoreVerticalObstacles`(state: BlockState, original: Operation<Boolean>): Boolean =
        HoseHeadPolicy.enabled(this as Any as HosePulleyBlockEntity) || original.call(state)

    @Inject(method = ["tick"], at = [At("TAIL")])
    private fun `teutonlib$tickHoseHead`(ci: CallbackInfo) {
        `teutonlib$clamp`()
        HosePulleyBridge.tick(this as Any as HosePulleyBlockEntity, offset.value)
    }

    @Inject(method = ["onSpeedChanged"], at = [At("TAIL")])
    private fun `teutonlib$stopHose`(previousSpeed: Float, ci: CallbackInfo) {
        `teutonlib$clamp`()
    }

    @Inject(method = ["lazyTick"], at = [At("TAIL")])
    private fun `teutonlib$keepHoseStopped`(ci: CallbackInfo) {
        `teutonlib$clamp`()
    }

    @Unique
    private fun `teutonlib$clamp`(): Boolean {
        val pulley = this as Any as HosePulleyBlockEntity
        if (!HoseHeadPolicy.enabled(pulley)) return false
        if (pulley.level == null) return false
        val limit = HosePulleyBridge.MAX_LENGTH
        if (offset.value < limit || offset.value == limit && pulley.speed <= 0) return false
        val changed = offset.value > limit || isMoving
        if (offset.value > limit) {
            offset.forceNextSync()
            offset.setValue(limit.toDouble())
        }
        isMoving = false
        if (changed && pulley.level?.isClientSide == false) {
            pulley.setChanged()
            pulley.sendData()
        }
        return true
    }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun `teutonlib$saveHoseHead`(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean, ci: CallbackInfo) {
        tag.putBoolean("TeutonLibHoseHeadEnabled", physicalHeadEnabled)
        physicalHeadId?.let { tag.putUUID("TeutonLibHoseHead", it) }
        physicalHeadPosition?.let { tag.putLong("TeutonLibHoseHeadPosition", it.asLong()) }
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun `teutonlib$loadHoseHead`(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean, ci: CallbackInfo) {
        physicalHeadEnabled = !tag.contains("TeutonLibHoseHeadEnabled") || tag.getBoolean("TeutonLibHoseHeadEnabled")
        physicalHeadId = if (tag.hasUUID("TeutonLibHoseHead")) tag.getUUID("TeutonLibHoseHead") else null
        physicalHeadPosition = if (tag.contains("TeutonLibHoseHeadPosition")) BlockPos.of(tag.getLong("TeutonLibHoseHeadPosition")) else null
    }
}
