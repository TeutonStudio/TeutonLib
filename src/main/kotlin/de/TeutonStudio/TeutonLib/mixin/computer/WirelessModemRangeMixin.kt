package de.TeutonStudio.TeutonLib.mixin.computer

import dan200.computercraft.api.network.PacketSender
import de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyVariable

/** Makes CC:Tweaked's normal-modem altitude calculation consume the projected world position. */
@Mixin(targets = ["dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral"], remap = false)
abstract class WirelessModemRangeMixin {
    @ModifyVariable(method = ["getRange"], at = At(value = "STORE"), ordinal = 0)
    private fun `teutonlib$worldPositionForAltitude`(position: Vec3): Vec3 {
        val level = (this as Any as PacketSender).level ?: return position
        return SubLevelGeometry.projectToWorld(level, position)
    }
}
