package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dan200.computercraft.api.lua.GenericSource
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.gyroscopic_propeller_bearing.GyroscopicPropellerBearingBlockEntity

/** Adds the flight-control methods to directly attached, wired and linked gyro peripherals. */
internal object BearingGenericMethods : GenericSource {
    override fun id(): String = "teutonlib:gyroscopic_bearing"

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun adjustTilt(bearing: GyroscopicPropellerBearingBlockEntity, values: Map<*, *>) {
        BearingControl.adjustTilt(bearing, values)
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun setTargetDirection(bearing: GyroscopicPropellerBearingBlockEntity, values: Map<*, *>) {
        BearingControl.setTargetDirection(bearing, values)
    }
}
