package de.TeutonStudio.TeutonLib.mixin.peripheral.simulated

import dev.simulated_team.simulated.compat.computercraft.peripherals.SimPeripheral
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(value = [SimPeripheral::class], remap = false)
interface SimPeripheralAccessor {
    @Accessor("blockEntity")
    fun `teutonlib$blockEntity`(): BlockEntity
}
