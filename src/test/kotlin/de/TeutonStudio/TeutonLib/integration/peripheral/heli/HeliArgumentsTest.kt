package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dan200.computercraft.api.lua.LuaException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HeliArgumentsTest {
    private val direction = setOf("x", "y", "z")

    @Test
    fun acceptsExactlyFiniteNumericFields() {
        assertEquals(
            mapOf("x" to 1.0, "y" to 2.0, "z" to 3.0),
            HeliArguments.vectorTable(mapOf("x" to 1, "y" to 2.0f, "z" to 3L), direction)
        )
    }

    @Test
    fun rejectsMissingExtraNonNumericAndNonFiniteFields() {
        assertThrows(LuaException::class.java) {
            HeliArguments.vectorTable(mapOf("x" to 1, "y" to 2), direction)
        }
        assertThrows(LuaException::class.java) {
            HeliArguments.vectorTable(mapOf("x" to 1, "y" to 2, "z" to 3, "w" to 4), direction)
        }
        assertThrows(LuaException::class.java) {
            HeliArguments.vectorTable(mapOf("x" to 1, "y" to "up", "z" to 3), direction)
        }
        assertThrows(LuaException::class.java) {
            HeliArguments.vectorTable(mapOf("x" to Double.NaN, "y" to 2, "z" to 3), direction)
        }
    }
}
