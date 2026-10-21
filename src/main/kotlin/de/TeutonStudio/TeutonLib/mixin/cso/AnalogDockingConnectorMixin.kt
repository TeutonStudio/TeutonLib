package de.TeutonStudio.TeutonLib.mixin.cso

import de.TeutonStudio.TeutonLib.integration.cso.docking.*
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity.DockingConnectorState
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.*
import org.spongepowered.asm.mixin.gen.Invoker
import org.spongepowered.asm.mixin.injection.*
import org.spongepowered.asm.mixin.injection.callback.*

@Pseudo
@Mixin(targets = ["dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity"], priority = 900, remap = false)
abstract class AnalogDockingConnectorMixin : AnalogDockingAccess {
    @field:Unique override var analogActive = false
    @field:Unique override var requestedLength = DockingLength.OFF
    @field:Unique override var effectiveLength = DockingLength.OFF
    @field:Unique override var selectedPartner: BlockPos? = null
    @field:Unique private var analogLastSentState = DockingConnectorState.UNPOWERED
    @Shadow protected var state = DockingConnectorState.UNPOWERED
    @Shadow protected var closestPairDistance = 0.0
    override var dockingState: DockingConnectorState
        get() = state
        set(value) { state = value }
    override var closestDistance: Double
        get() = closestPairDistance
        set(value) { closestPairDistance = value }
    @Invoker("updateState") abstract override fun updateDockingState()

    @Inject(method = ["tick"], at = [At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;tick()V", shift = At.Shift.AFTER)], cancellable = true)
    private fun analogTick(ci: CallbackInfo) {
        val self = this as Any as DockingConnectorBlockEntity
        if (AnalogDocking.active(self)) {
            AnalogDocking.tick(self)
            if (self.level?.isClientSide == false && analogLastSentState != state) {
                analogLastSentState = state
                self.setChanged()
                self.sendData()
            }
            ci.cancel()
        } else if (analogActive && self.level?.isClientSide == false) AnalogDocking.restoreOriginal(self)
    }

    // Keep SmartBlockEntity initialization (including MagnetBehaviour registration), but
    // never recreate a saved constraint with force=true before revalidating live inputs.
    @Inject(method = ["initialize"], at = [At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;initialize()V", shift = At.Shift.AFTER)], cancellable = true)
    private fun analogInitialize(ci: CallbackInfo) {
        val self = this as Any as DockingConnectorBlockEntity
        if (AnalogDocking.active(self)) {
            if (self.level?.isClientSide == false) AnalogDocking.detach(self)
            ci.cancel()
        }
    }

    @Inject(method = ["updateSignal"], at = [At("HEAD")], cancellable = true)
    private fun analogSignal(ci: CallbackInfo) {
        val self = this as Any as DockingConnectorBlockEntity
        if (AnalogDocking.active(self)) ci.cancel() // Server tick reads the actual 0..15 value.
    }

    @Inject(method = ["isExtended"], at = [At("HEAD")], cancellable = true)
    private fun analogExtended(ci: CallbackInfoReturnable<Boolean>) {
        val self = this as Any as DockingConnectorBlockEntity
        if (AnalogDocking.active(self)) ci.returnValue = self.powered && effectiveLength != DockingLength.OFF && self.extension.value == effectiveLength.blocks
    }

    @Inject(method = ["getTipPosition"], at = [At("HEAD")], cancellable = true)
    private fun analogTip(ci: CallbackInfoReturnable<Vec3>) {
        val self = this as Any as DockingConnectorBlockEntity
        if (AnalogDocking.active(self)) ci.returnValue = self.blockPos.center.add(Vec3.atLowerCornerOf(self.blockState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING).normal).scale(effectiveLength.contactOffset))
    }

    @Inject(method = ["getMagnetPosition"], at = [At("HEAD")], cancellable = true)
    private fun analogMagnet(ci: CallbackInfoReturnable<Vec3>) {
        val self = this as Any as DockingConnectorBlockEntity
        if (AnalogDocking.active(self)) ci.returnValue = self.blockPos.center.add(Vec3.atLowerCornerOf(self.blockState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING).normal).scale(effectiveLength.contactOffset - .1))
    }

    @Inject(method = ["setDock"], at = [At("TAIL")])
    private fun analogInventory(ci: CallbackInfo) {
        val self = this as Any as DockingConnectorBlockEntity
        // Upstream normally enables insertion lazily from getInventory on each endpoint.
        // Enable both original solo buffers when locked, even if only one side is queried.
        if (analogActive && self.isLocked) self.inventory.dock()
    }

    @Inject(method = ["remove"], at = [At("HEAD")])
    private fun analogRemove(ci: CallbackInfo) {
        val self = this as Any as DockingConnectorBlockEntity
        if (analogActive && self.level?.isClientSide == false) AnalogDocking.detach(self)
    }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun analogWrite(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean, ci: CallbackInfo) {
        val data = CompoundTag()
        data.putBoolean("Active", analogActive)
        data.putInt("Requested", requestedLength.ordinal)
        data.putInt("Effective", effectiveLength.ordinal)
        data.putInt("State", state.ordinal)
        tag.put("TeutonLibAnalogDocking", data)
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun analogRead(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean, ci: CallbackInfo) {
        val data = tag.getCompound("TeutonLibAnalogDocking")
        analogActive = data.getBoolean("Active")
        requestedLength = DockingLength.fromSaved(data.getInt("Requested"))
        effectiveLength = DockingLength.fromSaved(data.getInt("Effective"))
        selectedPartner = null // Selection is transient; upstream persists connectedPartner.
        if (clientPacket && analogActive) state = DockingConnectorState.entries.getOrNull(data.getInt("State")) ?: DockingConnectorState.UNPOWERED
    }
}
