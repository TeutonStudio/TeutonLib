package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.core.asm.GenericMethod
import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.gyroscopic_propeller_bearing.GyroscopicPropellerBearingBlockEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BearingGenericMethodsTest {
    @Test
    fun directPeripheralMethodsTargetAllGyroscopicBearingSubclasses() {
        val methods = BearingGenericMethods.javaClass.methods
            .filter { it.getAnnotation(LuaFunction::class.java) != null }
            .associateBy { it.name }

        assertEquals(setOf("adjustTilt", "setTargetDirection"), methods.keys)
        methods.values.forEach { method ->
            assertEquals(GyroscopicPropellerBearingBlockEntity::class.java, method.parameterTypes.first())
            assertTrue(method.getAnnotation(LuaFunction::class.java).mainThread)
        }
        assertEquals(
            setOf("adjustTilt", "setTargetDirection"),
            GenericMethod.getMethods(BearingGenericMethods).map(GenericMethod::name).toList().toSet()
        )
    }
}
