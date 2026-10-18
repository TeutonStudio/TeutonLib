package de.TeutonStudio.TeutonLib.api.computer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SourceViewTest {
    @Test fun `provider collection is copied and cannot mutate the published view`() {
        val original = mutableListOf("inventory")
        val view = SourceView("left", original, "local", null, null)
        original.add("fluid_storage")
        assertEquals(listOf("inventory"), view.types)
        assertThrows(UnsupportedOperationException::class.java) { (view.types as MutableList<String>).clear() }
    }
}
