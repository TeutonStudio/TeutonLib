package de.TeutonStudio.TeutonLib.integration.peripheral

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IDynamicLuaObject
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IPeripheral
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ExtendedLinkDeviceTest {
    @Test
    fun preservesExistingMethodsAndAddsMethodsToWrappedHandle() {
        val device = ExtendedLinkDevice(FakeDevice(), mapOf("getYaw" to LinkExtensionMethod { _, _ -> MethodResult.of(90.0) }))
        assertEquals(setOf("getAngles", "getYaw"), device.methods())
        assertArrayEquals(
            arrayOf("getAngles", "getPeripheralInfo", "getYaw"),
            (device.handle() as IDynamicLuaObject).methodNames
        )
    }

    @Test
    fun rejectsMethodNameCollisions() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtendedLinkDevice(FakeDevice(), mapOf("getAngles" to LinkExtensionMethod { _, _ -> MethodResult.of() }))
        }
    }

    private class FakeDevice : LinkDevice {
        private val target = object : IPeripheral {
            override fun getType(): String = "gimbal_sensor"
            override fun equals(other: IPeripheral?): Boolean = this === other
        }

        override fun peripheral(): IPeripheral = target
        override fun methods(): Set<String> = setOf("getAngles")
        override fun call(context: ILuaContext, method: String, arguments: IArguments): MethodResult = MethodResult.of()
        override fun handle(): Any = error("not used")
        override fun info(): Map<String, Any?> = mapOf("connectionType" to "wireless")
    }
}
