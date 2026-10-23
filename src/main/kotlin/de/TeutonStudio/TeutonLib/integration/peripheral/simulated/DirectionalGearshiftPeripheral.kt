package de.TeutonStudio.TeutonLib.integration.peripheral.simulated

import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral
import dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock
import dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlockEntity
import java.util.Locale
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BooleanProperty

/** A complete CC peripheral for Simulated's otherwise redstone-only directional gearshift. */
class DirectionalGearshiftPeripheral(
    private val blockEntity: DirectionalGearshiftBlockEntity
) : IPeripheral {
    override fun getType(): String = "directional_gearshift"

    override fun getTarget(): Any = blockEntity

    override fun equals(other: IPeripheral?): Boolean =
        other is DirectionalGearshiftPeripheral && other.blockEntity === blockEntity

    @LuaFunction(mainThread = true)
    fun setOutput(side: String, powered: Boolean) {
        set(side, powered)
    }

    @LuaFunction(mainThread = true)
    fun getOutput(side: String): Boolean = get(side)

    @LuaFunction(mainThread = true)
    fun setAnalogOutput(side: String, strength: Int) {
        if (strength !in 0..15) throw LuaException("Expected redstone strength between 0 and 15")
        set(side, strength > 0)
    }

    @LuaFunction(mainThread = true)
    fun getAnalogOutput(side: String): Int = if (get(side)) 15 else 0

    @LuaFunction(mainThread = true)
    fun isLeftPowered(): Boolean = powered(DirectionalGearshiftBlock.LEFT_POWERED)

    @LuaFunction(mainThread = true)
    fun isRightPowered(): Boolean = powered(DirectionalGearshiftBlock.RIGHT_POWERED)

    @LuaFunction(mainThread = true)
    fun setLeft(powered: Boolean) {
        applyOutputs(powered, isRightPowered())
    }

    @LuaFunction(mainThread = true)
    fun setRight(powered: Boolean) {
        applyOutputs(isLeftPowered(), powered)
    }

    @LuaFunction(mainThread = true)
    fun setOutputs(left: Boolean, right: Boolean) {
        applyOutputs(left, right)
    }

    @LuaFunction(mainThread = true)
    fun clear() {
        applyOutputs(left = false, right = false)
    }

    @LuaFunction(mainThread = true)
    fun getRotationModifier(face: String): Int {
        val direction = Direction.byName(face.lowercase(Locale.ROOT))
            ?: throw LuaException("Unknown direction '$face'")
        return Math.round(blockEntity.getRotationSpeedModifier(direction))
    }

    @LuaFunction(mainThread = true)
    fun getStatus(): Map<String, Any> = linkedMapOf(
        "leftPowered" to isLeftPowered(),
        "rightPowered" to isRightPowered(),
        "sourceFacing" to getFacing(),
        "blockPos" to getPosition(),
        "className" to getClassName()
    )

    @LuaFunction(mainThread = true)
    fun getFacing(): String = if (blockEntity.hasSource()) blockEntity.sourceFacing.name else "none"

    @LuaFunction
    fun getPosition(): Map<String, Int> = blockEntity.blockPos.let { pos ->
        linkedMapOf("x" to pos.x, "y" to pos.y, "z" to pos.z)
    }

    @LuaFunction
    fun getClassName(): String = blockEntity.javaClass.name

    @LuaFunction
    fun getName(): String = ""

    /** Simulated's gearshift has no custom-name storage; retained as a compatibility no-op. */
    @LuaFunction(mainThread = true)
    fun setName(name: String) {
        // Keep compatibility with the peripheral supplied by Create Gadgets & Gizmos.
    }

    @LuaFunction
    fun help(): String =
        "Directional gearshift API: setOutput(side, boolean), getOutput(side), " +
            "setAnalogOutput(side, 0..15), getAnalogOutput(side), setLeft(boolean), " +
            "setRight(boolean), setOutputs(boolean, boolean), clear(), getStatus()"

    @Throws(LuaException::class)
    private fun set(side: String, powered: Boolean) {
        when (side.lowercase(Locale.ROOT)) {
            "left" -> setLeft(powered)
            "right" -> setRight(powered)
            else -> throw LuaException("Unknown output '$side'; expected 'left' or 'right'")
        }
    }

    @Throws(LuaException::class)
    private fun get(side: String): Boolean = when (side.lowercase(Locale.ROOT)) {
        "left" -> isLeftPowered()
        "right" -> isRightPowered()
        else -> throw LuaException("Unknown output '$side'; expected 'left' or 'right'")
    }

    private fun powered(property: BooleanProperty): Boolean = currentState().getValue(property)

    private fun applyOutputs(left: Boolean, right: Boolean) {
        val current = currentState()
        val updated = current
            .setValue(DirectionalGearshiftBlock.LEFT_POWERED, left)
            .setValue(DirectionalGearshiftBlock.RIGHT_POWERED, right)
        if (updated == current) return

        val level = blockEntity.level ?: throw LuaException("Directional gearshift is not loaded")
        val block = current.block as DirectionalGearshiftBlock
        blockEntity.setChanged()
        block.detachKinetics(level, blockEntity.blockPos, true)
        level.setBlock(blockEntity.blockPos, updated, Block.UPDATE_CLIENTS)
    }

    private fun currentState(): BlockState {
        val state = blockEntity.blockState
        if (blockEntity.isRemoved || state.block !is DirectionalGearshiftBlock ||
            !state.hasProperty(DirectionalGearshiftBlock.LEFT_POWERED) ||
            !state.hasProperty(DirectionalGearshiftBlock.RIGHT_POWERED)
        ) {
            throw LuaException("Directional gearshift is no longer available")
        }
        return state
    }
}
