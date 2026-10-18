package de.TeutonStudio.TeutonLib.api.computer

import dan200.computercraft.api.peripheral.IPeripheral
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity

/** Server-thread view. Peripherals are borrowed and must not be attached here. */
interface ComputerContext {
    /** Returns an already loaded owning block; never loads chunks. */
    fun host(): BlockEntity? = null
    fun computerId(): Int
    fun level(): ServerLevel
    fun position(): BlockPos
    fun peripherals(): Map<String, IPeripheral>
    fun queueEvent(event: String, vararg arguments: Any?)
}
