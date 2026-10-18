package de.TeutonStudio.TeutonLib.mixin.computer

import dan200.computercraft.core.computer.Computer
import dan200.computercraft.shared.computer.core.ServerComputer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(ServerComputer::class, remap = false)
interface ServerComputerAccessor {
    @Accessor("computer")
    fun `teutonlib$computer`(): Computer
}
